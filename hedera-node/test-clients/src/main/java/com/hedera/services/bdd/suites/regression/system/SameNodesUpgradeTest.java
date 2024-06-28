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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.junit.TestTags.UPGRADE;
import static com.hedera.services.bdd.junit.hedera.MarkerFile.EXEC_IMMEDIATE_MF;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.buildUpgradeZipFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNetworkWithConfigVersion;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateAddressBooks;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForMf;
import static com.hedera.services.bdd.spec.utilops.upgrade.BuildUpgradeZipOp.FAKE_UPGRADE_ZIP_LOC;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_FILE_ID;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.FAKE_ASSETS_LOC;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileAppendsPerBurst;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileHashAt;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.TxnUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(UPGRADE)
public class SameNodesUpgradeTest implements LifecycleTest {
    private static final Duration EXEC_IMMEDIATE_MF_TIMEOUT = Duration.ofSeconds(10);

    @HapiTest
    final Stream<DynamicTest> upgradeSoftwareVersionWithSameAddressBook() {
        final AtomicReference<SemanticVersion> version = new AtomicReference<>();
        return hapiTest(
                buildUpgradeZipFrom(FAKE_ASSETS_LOC),
                getVersionInfo().exposingServicesVersionTo(version::set),
                // Upload it to file 0.0.150; need sourcing() here because the operation reads contents eagerly
                sourcing(() -> updateSpecialFile(
                        GENESIS,
                        DEFAULT_UPGRADE_FILE_ID,
                        FAKE_UPGRADE_ZIP_LOC,
                        TxnUtils.BYTES_4K,
                        upgradeFileAppendsPerBurst())),
                // Issue PREPARE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> prepareUpgrade()
                        .withUpdateFile(DEFAULT_UPGRADE_FILE_ID)
                        .havingHash(upgradeFileHashAt(FAKE_UPGRADE_ZIP_LOC))),
                // Wait for the immediate execution marker file (written only after 0.0.150 is unzipped)
                waitForMf(EXEC_IMMEDIATE_MF, EXEC_IMMEDIATE_MF_TIMEOUT),
                // Validate all four nodes are written
                validateAddressBooks(
                        addressBook -> assertEquals(4, addressBook.getSize(), "Wrong number of nodes in address book")),
                // Issue FREEZE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> freezeUpgrade()
                        .startingIn(2)
                        .seconds()
                        .withUpdateFile(DEFAULT_UPGRADE_FILE_ID)
                        .havingHash(upgradeFileHashAt(FAKE_UPGRADE_ZIP_LOC))),
                confirmFreezeAndShutdown(),
                restartNetworkWithConfigVersion(1),
                // Wait for all nodes to be ACTIVE
                waitForActiveNetwork(RESTART_TIMEOUT),
                // Confirm the version changed
                sourcing(() -> getVersionInfo().hasProtoServicesVersion(withExpectedConfigVersion(version.get(), 1))));
    }

    private SemanticVersion withExpectedConfigVersion(@NonNull final SemanticVersion version, final int configVersion) {
        return version.toBuilder().setBuild("" + configVersion).build();
    }
}
