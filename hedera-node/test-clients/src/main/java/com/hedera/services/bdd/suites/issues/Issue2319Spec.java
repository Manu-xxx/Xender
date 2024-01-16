/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@HapiTestSuite
public class Issue2319Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue2319Spec.class);

    private String pemLoc = "src/main/resource/randomCivilianKey.pem";
    private String passphrase = "passphrase";

    public static void main(String... args) {
        new Issue2319Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            sysFileImmutabilityWaivedForMasterAndTreasury(),
            propsPermissionsSigReqsWaivedForAddressBookAdmin(),
            sysAccountSigReqsWaivedForMasterAndTreasury(),
            sysFileSigReqsWaivedForMasterAndTreasury()
        });
    }

    @Order(2)
    @HapiTest
    final HapiSpec propsPermissionsSigReqsWaivedForAddressBookAdmin() {
        return defaultHapiSpec("PropsPermissionsSigReqsWaivedForAddressBookAdmin")
                .given(
                        keyFromPem(pemLoc).name("persistent").passphrase(passphrase),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)))
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .wacl("persistent"),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .wacl("persistent"))
                .then(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("claimHashSize", "49"))
                                .signedBy(GENESIS),
                        fileUpdate(API_PERMISSIONS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .overridingProps(Map.of("claimHashSize", "49"))
                                .signedBy(GENESIS),
                        fileUpdate(APP_PROPERTIES).wacl(GENESIS),
                        fileUpdate(API_PERMISSIONS).wacl(GENESIS));
    }

    @Order(1)
    @HapiTest
    final HapiSpec sysFileImmutabilityWaivedForMasterAndTreasury() {
        return defaultHapiSpec("sysFileImmutabilityWaivedForMasterAndTreasury")
                .given(
                        cryptoCreate("civilian"),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)))
                .when(fileUpdate(EXCHANGE_RATES)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .useEmptyWacl())
                .then(
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .wacl(GENESIS)
                                .payingWith(SYSTEM_ADMIN)
                                .signedBy(GENESIS),
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .useEmptyWacl(),
                        fileUpdate(EXCHANGE_RATES)
                                .wacl(GENESIS)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS));
    }

    @Order(3)
    @HapiTest
    final HapiSpec sysAccountSigReqsWaivedForMasterAndTreasury() {
        return defaultHapiSpec("SysAccountSigReqsWaivedForMasterAndTreasury")
                .given(
                        cryptoCreate("civilian"),
                        keyFromPem(pemLoc).name("persistent").passphrase(passphrase),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)))
                .when(cryptoUpdate(EXCHANGE_RATE_CONTROL).key("persistent"))
                .then(
                        cryptoUpdate(EXCHANGE_RATE_CONTROL)
                                .payingWith(SYSTEM_ADMIN)
                                .signedBy(GENESIS)
                                .receiverSigRequired(true),
                        cryptoUpdate(EXCHANGE_RATE_CONTROL)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .receiverSigRequired(true),
                        cryptoUpdate(EXCHANGE_RATE_CONTROL)
                                .payingWith("civilian")
                                .signedBy("civilian", GENESIS, "persistent")
                                .receiverSigRequired(true),
                        cryptoUpdate(EXCHANGE_RATE_CONTROL).key("persistent").receiverSigRequired(false));
    }

    @Order(4)
    @HapiTest
    final HapiSpec sysFileSigReqsWaivedForMasterAndTreasury() {
        var validRates = new AtomicReference<ByteString>();

        return defaultHapiSpec("SysFileSigReqsWaivedForMasterAndTreasury")
                .given(
                        cryptoCreate("civilian"),
                        keyFromPem(pemLoc).name("persistent").passphrase(passphrase),
                        withOpContext((spec, opLog) -> {
                            var fetch = getFileContents(EXCHANGE_RATES);
                            CustomSpecAssert.allRunFor(spec, fetch);
                            validRates.set(fetch.getResponse()
                                    .getFileGetContents()
                                    .getFileContents()
                                    .getContents());
                        }),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)))
                .when(fileUpdate(EXCHANGE_RATES)
                        .payingWith(EXCHANGE_RATE_CONTROL)
                        .wacl("persistent"))
                .then(
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(SYSTEM_ADMIN)
                                .signedBy(GENESIS)
                                .contents(ignore -> validRates.get()),
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(GENESIS)
                                .signedBy(GENESIS)
                                .contents(ignore -> validRates.get()),
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith("civilian")
                                .signedBy("civilian", GENESIS, "persistent")
                                .contents(ignore -> validRates.get())
                                .hasPrecheck(AUTHORIZATION_FAILED),
                        fileUpdate(EXCHANGE_RATES).payingWith(GENESIS).wacl(GENESIS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
