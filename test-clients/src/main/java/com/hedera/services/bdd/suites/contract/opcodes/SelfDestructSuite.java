package com.hedera.services.bdd.suites.contract.opcodes;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SELF_DESTRUCT_CALLABLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SelfDestructSuite extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger(SelfDestructSuite.class);

    public static void main(String... args) {
        new SelfDestructSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                HSCS_EVM_008_SelfDesctructUpdatesHederaAccount(),
                HSCS_EVM_008_SelfDestructWhenCalling()
        );
    }

    private HapiApiSpec HSCS_EVM_008_SelfDesctructUpdatesHederaAccount() {
        return defaultHapiSpec("HSCS_EVM_008_SelfDestructUpdatesHederaAccount")
                .given(
                        fileCreate("bytecode")
                                .path(FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT)
                )
                .when(
                        contractCreate("selfDestroying")
                                .bytecode("bytecode")
                                .via("contractCreate")
                                .hasKnownStatus(SUCCESS)
                )
                .then(
                        getAccountInfo("selfDestroying")
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo("selfDestroying")
                                .hasCostAnswerPrecheck(CONTRACT_DELETED)
                );
    }

    private HapiApiSpec HSCS_EVM_008_SelfDestructWhenCalling() {
        return defaultHapiSpec("HSCS_EVM_008_SelfDestructWhenCalling")
                .given(
                        cryptoCreate("acc").balance(ONE_HUNDRED_HBARS),
                        fileCreate("bytecode").path(SELF_DESTRUCT_CALLABLE).payingWith("acc")
                )
                .when(
                        contractCreate("destructable")
                                .bytecode("bytecode")
                                .via("cc")
                                .payingWith("acc")
                                .hasKnownStatus(SUCCESS)
                )
                .then(
                        contractCall("destructable", ContractResources.SELF_DESTRUCT_CALL_ABI)
                                .payingWith("acc"),
                        getAccountInfo("destructable")
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo("destructable")
                                .hasCostAnswerPrecheck(CONTRACT_DELETED)
                );
    }
}
