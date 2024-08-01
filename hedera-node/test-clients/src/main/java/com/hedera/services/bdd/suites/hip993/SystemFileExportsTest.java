/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromByteString;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener.CLASSIC_HAPI_TEST_NETWORK_SIZE;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDnsServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.given;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nOps;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.simulatePostUpgradeTransaction;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.grouping.GroupingVerbs.getSystemFiles;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.grouping.SysFileLookups;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItems;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsValidator;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Asserts the synthetic file creations stipulated by HIP-993 match the file contents returned by the gRPC
 * API both before after the network has handled the genesis transaction. (It would be a annoyance for various tools
 * and tests if they needed to ensure a transaction was handled before issuing any {@code FileGetContents} queries
 * or submitting {@code FileUpdate} transactions.)
 */
@Isolated
public class SystemFileExportsTest {
    private static final int ACCOUNT_ID_OFFSET = 13;
    private static final String DESCRIPTION_PREFIX = "Revision #";

    @GenesisHapiTest
    final Stream<DynamicTest> syntheticFileUpdatesHappenAtUpgradeBoundary() {
        final var grpcCertHashes = new byte[][] {
            randomUtf8Bytes(48), randomUtf8Bytes(48), randomUtf8Bytes(48), randomUtf8Bytes(48),
        };
        final AtomicReference<Map<Long, X509Certificate>> gossipCertificates = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(visibleItems(
                        addressBookExportValidator(grpcCertHashes, gossipCertificates), "addressBookExport")),
                given(() -> gossipCertificates.set(generateCertificates(CLASSIC_HAPI_TEST_NETWORK_SIZE))),
                // This is the genesis transaction
                cryptoCreate("firstUser"),
                overriding("nodes.updateAccountIdAllowed", "true"),
                sourcing(() -> blockingOrder(nOps(CLASSIC_HAPI_TEST_NETWORK_SIZE, i -> nodeUpdate("" + i)
                        .accountId("0.0." + (i + ACCOUNT_ID_OFFSET))
                        .description(DESCRIPTION_PREFIX + i)
                        .serviceEndpoint(endpointsFor(i))
                        .grpcCertificateHash(grpcCertHashes[i])
                        .gossipCaCertificate(derEncoded(gossipCertificates.get().get(Long.valueOf(i))))))),
                // And now simulate an upgrade boundary
                simulatePostUpgradeTransaction(),
                cryptoCreate("secondUser").via("addressBookExport"));
    }

    @GenesisHapiTest
    final Stream<DynamicTest> syntheticFileCreationsMatchQueries() {
        final AtomicReference<Map<FileID, Bytes>> preGenesisContents = new AtomicReference<>();
        return hapiTest(
                streamMustIncludeNoFailuresFrom(visibleItems(validatorFor(preGenesisContents), "genesisTxn")),
                getSystemFiles(preGenesisContents::set),
                cryptoCreate("firstUser").via("genesisTxn"),
                // Assert the first created entity still has the expected number
                withOpContext((spec, opLog) -> assertEquals(
                        spec.startupProperties().getLong("hedera.firstUserEntity"),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    private static VisibleItemsValidator addressBookExportValidator(
            @NonNull final byte[][] grpcCertHashes,
            @NonNull final AtomicReference<Map<Long, X509Certificate>> gossipCertificates) {
        return (spec, records) -> {
            final var items = requireNonNull(records.get("addressBookExport"));
            final var histogram = statusHistograms(items.entries());
            assertEquals(Map.of(SUCCESS, 1), histogram.get(FileUpdate));
            final var updateItem = items.entries().stream()
                    .filter(item -> item.function() == FileUpdate)
                    .findFirst()
                    .orElseThrow();
            final var synthOp = updateItem.body().getFileUpdate();
            final var nodeDetailsId =
                    new FileID(0, 0, Long.parseLong(spec.startupProperties().get("files.nodeDetails")));
            assertEquals(nodeDetailsId, toPbj(synthOp.getFileID()));
            try {
                final var updatedAddressBook = NodeAddressBook.PROTOBUF.parse(
                        Bytes.wrap(synthOp.getContents().toByteArray()));
                for (final var address : updatedAddressBook.nodeAddress()) {
                    final var expectedCert = gossipCertificates.get().get(address.nodeId());
                    final var expectedPubKey = expectedCert.getPublicKey().getEncoded();
                    final var actualPubKey = unhex(address.rsaPubKey());
                    assertArrayEquals(expectedPubKey, actualPubKey, "node" + address.nodeId() + " has wrong RSA key");

                    final var actualCertHash = address.nodeCertHash().toByteArray();
                    assertArrayEquals(
                            grpcCertHashes[(int) address.nodeId()],
                            actualCertHash,
                            "node" + address.nodeId() + " has wrong cert hash");

                    final var expectedAccountID = AccountID.newBuilder()
                            .accountNum(address.nodeId() + ACCOUNT_ID_OFFSET)
                            .build();
                    assertEquals(expectedAccountID, address.nodeAccountId());

                    final var expectedDescription = DESCRIPTION_PREFIX + address.nodeId();
                    assertEquals(expectedDescription, address.description());

                    final var expectedServiceEndpoint = endpointsFor((int) address.nodeId());
                    assertEquals(expectedServiceEndpoint, address.serviceEndpoint());
                }
            } catch (ParseException e) {
                Assertions.fail("Update contents was not protobuf " + e.getMessage());
            }
        };
    }

    private static VisibleItemsValidator validatorFor(
            @NonNull final AtomicReference<Map<FileID, Bytes>> preGenesisContents) {
        return (spec, records) -> validateSystemFileExports(spec, records, preGenesisContents.get());
    }

    private static void validateSystemFileExports(
            @NonNull final HapiSpec spec,
            @NonNull final Map<String, VisibleItems> genesisRecords,
            @NonNull final Map<FileID, Bytes> preGenesisContents) {
        final var items = requireNonNull(genesisRecords.get("genesisTxn"));
        final var histogram = statusHistograms(items.entries());
        final var systemFileNums =
                SysFileLookups.allSystemFileNums(spec).boxed().toList();
        assertEquals(Map.of(SUCCESS, systemFileNums.size()), histogram.get(FileCreate));
        // Also check we export a node stake update at genesis
        assertEquals(Map.of(SUCCESS, 1), histogram.get(NodeStakeUpdate));
        final var postGenesisContents = SysFileLookups.getSystemFileContents(spec, fileNum -> true);
        items.entries().stream().filter(item -> item.function() == FileCreate).forEach(item -> {
            final var preContents = requireNonNull(
                    preGenesisContents.get(item.createdFileId()),
                    "No pre-genesis contents for " + item.createdFileId());
            final var postContents = requireNonNull(
                    postGenesisContents.get(item.createdFileId()),
                    "No post-genesis contents for " + item.createdFileId());
            final var exportedContents =
                    fromByteString(item.body().getFileCreate().getContents());
            assertEquals(
                    exportedContents, preContents, item.createdFileId() + " contents don't match pre-genesis query");
            assertEquals(
                    exportedContents, postContents, item.createdFileId() + " contents don't match post-genesis query");
        });
    }

    private static Map<Long, X509Certificate> generateCertificates(final int n) {
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(n)
                .withRealKeysEnabled(true)
                .build();
        final var nextNodeId = new AtomicLong();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(randomAddressBook.iterator(), 0), false)
                .map(Address::getSigCert)
                .collect(Collectors.toMap(cert -> nextNodeId.getAndIncrement(), cert -> cert));
    }

    private static byte[] derEncoded(final X509Certificate cert) {
        try {
            return cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Failed to DER encode cert", e);
        }
    }

    private static List<ServiceEndpoint> endpointsFor(final int i) {
        if (i % 2 == 0) {
            return List.of(asServiceEndpoint("127.0.0." + (i * 2 + 1) + ":" + (80 + i)));
        } else {
            return List.of(asDnsServiceEndpoint("host" + i + ":" + (80 + i)));
        }
    }
}
