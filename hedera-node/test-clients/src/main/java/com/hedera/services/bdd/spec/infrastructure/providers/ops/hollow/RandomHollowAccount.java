/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;

public class RandomHollowAccount implements OpProvider {
    // Added to hollow account names to differentiate them from the keys created for them
    public static final String ACCOUNT_SUFFIX = "#";
    public static final String KEY_PREFIX = "Fuzz#";
    public static final int DEFAULT_CEILING_NUM = 100;
    public static final long INITIAL_BALANCE = 1_000_000_000L;
    private int ceilingNum = DEFAULT_CEILING_NUM;
    private final HapiSpecRegistry registry;

    private final RegistrySourcedNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<AccountID> accounts;

    public RandomHollowAccount(
            HapiSpecRegistry registry,
            RegistrySourcedNameProvider<Key> keys,
            RegistrySourcedNameProvider<AccountID> accounts) {
        this.registry = registry;
        this.keys = keys;
        this.accounts = accounts;
    }

    public RandomHollowAccount ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        // doubling ceilingNum as keys are also saved in accounts registry when account is created
        if (accounts.numPresent() >= ceilingNum * 2) {
            return Optional.empty();
        }

        return randomKey().map(this::generateHollowAccount);
    }

    private Optional<String> randomKey() {
        return keys.getQualifying()
                .filter(k -> !k.endsWith(ACCOUNT_SUFFIX))
                .filter(k -> k.startsWith(KEY_PREFIX))
                .filter(k -> !registry.hasAccountId(k + ACCOUNT_SUFFIX));
    }

    private HapiSpecOperation generateHollowAccount(String keyName) {
        return withOpContext((spec, opLog) -> {
            final var evmAddress = getEvmAddress(keyName);
            final var op = cryptoTransfer(tinyBarsFromTo(GENESIS, evmAddress, ONE_HUNDRED_HBARS))
                    .hasKnownStatusFrom(standardOutcomesAnd(ACCOUNT_DELETED))
                    .via("LAZY_CREATE");

            final HapiGetTxnRecord hapiGetTxnRecord =
                    getTxnRecord("LAZY_CREATE").andAllChildRecords().assertingNothingAboutHashes();

            allRunFor(spec, op, hapiGetTxnRecord);

            if (hapiGetTxnRecord.getChildRecords().size() > 0) {
                final AccountID newAccountID =
                        hapiGetTxnRecord.getChildRecord(0).getReceipt().getAccountID();
                spec.registry().saveAccountId(keyName + ACCOUNT_SUFFIX, newAccountID);
            }
        });
    }

    private ByteString getEvmAddress(String keyName) {
        final var ecdsaKey = this.registry.getKey(keyName).getECDSASecp256K1().toByteArray();
        return ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
    }
}
