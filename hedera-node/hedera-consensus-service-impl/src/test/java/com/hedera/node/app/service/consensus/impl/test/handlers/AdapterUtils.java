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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.node.app.service.consensus.impl.test.handlers.AdapterUtils.SigReqAdapterUtils.wellKnownAccountStoreAt;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_PAYER_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_RECEIVER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_WIPE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

// NOTE: This class is duplicated in more than one service module.
// !!!!!!!!!!🔥🔥🔥 It should be deleted once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
public class AdapterUtils {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private static final OneOf<Account.StakedIdOneOfType> UNSET_STAKED_ID =
            new OneOf<>(Account.StakedIdOneOfType.UNSET, null);

    public static final AccountID PARITY_CUSTOM_PAYER =
            AccountID.newBuilder().accountNum(1216).build();
    public static final AccountID PARITY_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link ReadableAccountStore} containing the "well-known" accounts and aliases that
     * exist in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in
     * unit tests that require an {@link ReadableAccountStore}.
     *
     * @return the well-known account store
     */
    public static ReadableAccountStore wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(
                ALIASES_KEY, wellKnownAliasState(),
                ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = Mockito.mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static ReadableKVState<AccountID, Account> wellKnownAccountsState() {
        return new MapReadableKVState<>(ACCOUNTS_KEY, wellKnownAccountStoreAt());
    }

    private static MapReadableKVState<Bytes, AccountID> wellKnownAliasState() {
        final Map<Bytes, AccountID> wellKnownAliases = Map.ofEntries(
                Map.entry(Bytes.wrap(CURRENTLY_UNUSED_ALIAS), asAccount(MISSING_NUM.longValue())),
                Map.entry(Bytes.wrap(NO_RECEIVER_SIG_ALIAS), toPbj(NO_RECEIVER_SIG)),
                Map.entry(Bytes.wrap(RECEIVER_SIG_ALIAS), toPbj(RECEIVER_SIG)),
                Map.entry(Bytes.wrap(FIRST_TOKEN_SENDER_LITERAL_ALIAS.toByteArray()), toPbj(FIRST_TOKEN_SENDER)));
        return new MapReadableKVState<>(ALIASES_KEY, wellKnownAliases);
    }

    class SigReqAdapterUtils {
        private static final String ACCOUNTS_KEY = "ACCOUNTS";

        private static AccountCryptoAllowance cryptoAllowances = AccountCryptoAllowance.newBuilder()
                .spenderId(AccountID.newBuilder().accountNum(DEFAULT_PAYER.getAccountNum()))
                .amount(500L)
                .build();
        private static AccountFungibleTokenAllowance fungibleTokenAllowances =
                AccountFungibleTokenAllowance.newBuilder()
                        .tokenId(TokenID.newBuilder().tokenNum(KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum()))
                        .spenderId(AccountID.newBuilder().accountNum(DEFAULT_PAYER.getAccountNum()))
                        .amount(10_000L)
                        .build();

        private static AccountApprovalForAllAllowance nftAllowances = AccountApprovalForAllAllowance.newBuilder()
                .tokenId(TokenID.newBuilder().tokenNum(KNOWN_TOKEN_WITH_WIPE.getTokenNum()))
                .spenderId(AccountID.newBuilder().accountNum(DEFAULT_PAYER.getAccountNum()))
                .build();

        private static ReadableKVState<AccountID, Account> wellKnownAccountsState() {
            return new MapReadableKVState<>(ACCOUNTS_KEY, wellKnownAccountStoreAt());
        }

        public static Map<AccountID, Account> wellKnownAccountStoreAt() {
            final var destination = new HashMap<AccountID, Account>();
            destination.put(
                    toPbj(FIRST_TOKEN_SENDER),
                    toPbjAccount(FIRST_TOKEN_SENDER.getAccountNum(), FIRST_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
            destination.put(
                    toPbj(SECOND_TOKEN_SENDER),
                    toPbjAccount(SECOND_TOKEN_SENDER.getAccountNum(), SECOND_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
            destination.put(
                    toPbj(TOKEN_RECEIVER), toPbjAccount(TOKEN_RECEIVER.getAccountNum(), TOKEN_WIPE_KT.asPbjKey(), 0L));
            destination.put(
                    toPbj(DEFAULT_NODE), toPbjAccount(DEFAULT_NODE.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), 0L));
            destination.put(
                    toPbj(DEFAULT_PAYER),
                    toPbjAccount(DEFAULT_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    toPbj(STAKING_FUND),
                    toPbjAccount(STAKING_FUND.getAccountNum(), toPbj(asKeyUnchecked(EMPTY_KEY)), 0L));
            destination.put(
                    toPbj(MASTER_PAYER),
                    toPbjAccount(MASTER_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    toPbj(TREASURY_PAYER),
                    toPbjAccount(TREASURY_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    toPbj(NO_RECEIVER_SIG),
                    toPbjAccount(NO_RECEIVER_SIG.getAccountNum(), NO_RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(RECEIVER_SIG),
                    toPbjAccount(RECEIVER_SIG.getAccountNum(), RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE, true));
            destination.put(
                    toPbj(SYS_ACCOUNT),
                    toPbjAccount(SYS_ACCOUNT.getAccountNum(), SYS_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(MISC_ACCOUNT),
                    toPbjAccount(MISC_ACCOUNT.getAccountNum(), MISC_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(CUSTOM_PAYER_ACCOUNT),
                    toPbjAccount(
                            CUSTOM_PAYER_ACCOUNT.getAccountNum(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(OWNER_ACCOUNT),
                    toPbjAccount(
                            OWNER_ACCOUNT.getAccountNum(),
                            OWNER_ACCOUNT_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances)));
            destination.put(
                    toPbj(DELEGATING_SPENDER),
                    toPbjAccount(
                            DELEGATING_SPENDER.getAccountNum(),
                            DELEGATING_SPENDER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances)));
            destination.put(
                    toPbj(COMPLEX_KEY_ACCOUNT),
                    toPbjAccount(
                            COMPLEX_KEY_ACCOUNT.getAccountNum(), COMPLEX_KEY_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(TOKEN_TREASURY),
                    toPbjAccount(TOKEN_TREASURY.getAccountNum(), TOKEN_TREASURY_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    toPbj(DILIGENT_SIGNING_PAYER),
                    toPbjAccount(
                            DILIGENT_SIGNING_PAYER.getAccountNum(),
                            DILIGENT_SIGNING_PAYER_KT.asPbjKey(),
                            DEFAULT_BALANCE));
            destination.put(
                    toPbj(FROM_OVERLAP_PAYER),
                    toPbjAccount(
                            FROM_OVERLAP_PAYER.getAccountNum(), FROM_OVERLAP_PAYER_KT.asPbjKey(), DEFAULT_BALANCE));
            return destination;
        }

        private static Account toPbjAccount(final long number, final Key key, long balance) {
            return toPbjAccount(number, key, balance, false, List.of(), List.of(), List.of());
        }

        private static Account toPbjAccount(
                final long number, final Key key, long balance, final boolean isReceiverSigRequired) {
            return toPbjAccount(number, key, balance, isReceiverSigRequired, List.of(), List.of(), List.of());
        }

        private static Account toPbjAccount(
                final long number,
                final Key key,
                long balance,
                boolean receiverSigRequired,
                List<AccountCryptoAllowance> cryptoAllowances,
                List<AccountFungibleTokenAllowance> fungibleTokenAllowances,
                List<AccountApprovalForAllAllowance> nftTokenAllowances) {
            return new Account(
                    AccountID.newBuilder().accountNum(number).build(),
                    Bytes.EMPTY,
                    key,
                    10_000L,
                    balance,
                    "test",
                    false,
                    1_234_567L,
                    1_234_567L,
                    UNSET_STAKED_ID,
                    false,
                    receiverSigRequired,
                    TokenID.newBuilder().tokenNum(3L).build(),
                    NftID.newBuilder()
                            .tokenId(TokenID.newBuilder().tokenNum(2L))
                            .build(),
                    1L,
                    2,
                    3,
                    3,
                    3,
                    false,
                    3,
                    0,
                    1_234_5678L,
                    AccountID.newBuilder().accountNum(2L).build(),
                    76_000L,
                    0,
                    cryptoAllowances,
                    nftTokenAllowances,
                    fungibleTokenAllowances,
                    2,
                    false,
                    null);
        }
    }
}
