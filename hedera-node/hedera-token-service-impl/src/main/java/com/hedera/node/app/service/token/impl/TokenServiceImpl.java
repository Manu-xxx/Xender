/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.codec.MonoMapCodecAdapter;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.serdes.EntityNumCodec;
import com.hedera.node.app.service.token.impl.serdes.StringCodec;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    private static final int MAX_ACCOUNTS = 1024;
    private static final int MAX_TOKEN_RELS = 1042;
    private static final int MAX_MINTABLE_NFTS = 4096;
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;

    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String PAYER_RECORDS_KEY = "PAYER_RECORDS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(tokenSchema());
    }

    private Schema tokenSchema() {
        // Everything on disk that can be
        return new Schema(GENESIS_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        tokensDef(),
                        onDiskAccountsDef(),
                        onDiskAliasesDef(),
                        onDiskNftsDef(),
                        onDiskTokenRelsDef(),
                        payerRecordsDef(),
                        stakingInfoDef());
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                // TBD Verify this is correct. We need to preload all the special accounts
                final var accounts = ctx.newStates().get(ACCOUNTS_KEY);
                final var accountsConfig = ctx.configuration().getConfigData(AccountsConfig.class);
                final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
                final var ledgerConfig = ctx.configuration().getConfigData(LedgerConfig.class);
                final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
                final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
                if (superUserKeyBytes.length() != 32) {
                    throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
                }
                final var superUserKey =
                        Key.newBuilder().ed25519(superUserKeyBytes).build();

                final var numSystemAccounts = ledgerConfig.numSystemAccounts();
                final var expiry = bootstrapConfig.systemEntityExpiry();
                final var tinyBarFloat = ledgerConfig.totalTinyBarFloat();

                for (long num = 1; num <= numSystemAccounts; num++) {
                    final var id = AccountID.newBuilder()
                            .shardNum(hederaConfig.shard())
                            .realmNum(hederaConfig.realm())
                            .accountNum(num)
                            .build();

                    if (accounts.contains(id)) {
                        continue;
                    }

                    final var accountTinyBars = num == accountsConfig.treasury() ? tinyBarFloat : 0L;
                    assert accountTinyBars >= 0L : "Negative account balance!";

                    accounts.put(
                            id,
                            Account.newBuilder()
                                    .receiverSigRequired(false)
                                    .deleted(false)
                                    .expiry(expiry)
                                    .memo("")
                                    .smartContract(false)
                                    .key(superUserKey)
                                    .autoRenewSecs(expiry) // TODO is this right?
                                    .accountId(id)
                                    .tinybarBalance(accountTinyBars)
                                    //                                    .declineReward(true)
                                    .build());
                }
            }
        };
    }

    private StateDefinition<AccountID, Account> onDiskAccountsDef() {
        final var keySerdes = AccountID.PROTOBUF;
        final var valueSerdes = Account.PROTOBUF;
        return StateDefinition.onDisk(ACCOUNTS_KEY, keySerdes, valueSerdes, MAX_ACCOUNTS);
    }

    private StateDefinition<String, EntityNumValue> onDiskAliasesDef() {
        final var keySerdes = new StringCodec();
        final var valueSerdes =
                MonoMapCodecAdapter.codecForVirtualValue(EntityNumValue.CURRENT_VERSION, EntityNumValue::new);
        return StateDefinition.onDisk(ALIASES_KEY, keySerdes, valueSerdes, MAX_ACCOUNTS);
    }

    private StateDefinition<EntityNum, MerklePayerRecords> payerRecordsDef() {
        final var keySerdes = new EntityNumCodec();
        final var valueSerdes = MonoMapCodecAdapter.codecForSelfSerializable(
                MerklePayerRecords.CURRENT_VERSION, MerklePayerRecords::new);
        return StateDefinition.inMemory(PAYER_RECORDS_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<EntityNum, MerkleToken> tokensDef() {
        final var keySerdes = new EntityNumCodec();
        final var valueSerdes =
                MonoMapCodecAdapter.codecForSelfSerializable(MerkleToken.CURRENT_VERSION, MerkleToken::new);
        return StateDefinition.inMemory(TOKENS_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<EntityNumVirtualKey, OnDiskTokenRel> onDiskTokenRelsDef() {
        final var keySerdes = MonoMapCodecAdapter.codecForVirtualKey(
                EntityNumVirtualKey.CURRENT_VERSION, EntityNumVirtualKey::new, new EntityNumVirtualKeySerializer());
        final var valueSerdes =
                MonoMapCodecAdapter.codecForVirtualValue(OnDiskTokenRel.CURRENT_VERSION, OnDiskTokenRel::new);
        return StateDefinition.onDisk(TOKEN_RELS_KEY, keySerdes, valueSerdes, MAX_TOKEN_RELS);
    }

    private StateDefinition<UniqueTokenKey, UniqueTokenValue> onDiskNftsDef() {
        final var keySerdes = MonoMapCodecAdapter.codecForVirtualKey(
                UniqueTokenKey.CURRENT_VERSION, UniqueTokenKey::new, new UniqueTokenKeySerializer());
        final var valueSerdes =
                MonoMapCodecAdapter.codecForVirtualValue(UniqueTokenValue.CURRENT_VERSION, UniqueTokenValue::new);
        return StateDefinition.onDisk(NFTS_KEY, keySerdes, valueSerdes, MAX_MINTABLE_NFTS);
    }

    private StateDefinition<EntityNum, MerkleStakingInfo> stakingInfoDef() {
        final var keySerdes = new EntityNumCodec();
        final var valueSerdes =
                MonoMapCodecAdapter.codecForSelfSerializable(MerkleStakingInfo.CURRENT_VERSION, MerkleStakingInfo::new);
        return StateDefinition.inMemory(STAKING_INFO_KEY, keySerdes, valueSerdes);
    }
}
