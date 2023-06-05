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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

public class NetworkAdminHandlerTestBase {
    public static final String ACCOUNTS = "ACCOUNTS";
    protected static final String TOKENS = "TOKENS";
    protected static final String TOKEN_RELS = "TOKEN_RELS";

    protected final Bytes ledgerId = Bytes.wrap(new byte[] {3});

    protected final AccountID id = AccountID.newBuilder().accountNum(3).build();
    protected final Long accountNum = id.accountNum();
    protected final EntityNumVirtualKey accountEntityNumVirtualKey = new EntityNumVirtualKey(accountNum);
    protected final AccountID alias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();

    protected final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    protected final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();
    protected final Long deleteAccountNum = deleteAccountId.accountNum();
    protected final Long transferAccountNum = transferAccountId.accountNum();

    protected static final long payerBalance = 10_000L;
    protected final EntityNum fungibleTokenNum = EntityNum.fromLong(1L);
    protected final EntityNum nonFungibleTokenNum = EntityNum.fromLong(2L);
    protected final EntityNumPair fungiblePair =
            EntityNumPair.fromLongs(accountNum.longValue(), fungibleTokenNum.longValue());
    protected final EntityNumPair nonFungiblePair =
            EntityNumPair.fromLongs(accountNum.longValue(), nonFungibleTokenNum.longValue());

    protected final TokenID tokenId =
            TokenID.newBuilder().tokenNum(fungibleTokenNum.longValue()).build();

    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccounts;
    protected MapReadableKVState<EntityNum, Token> readableTokenState;
    protected MapReadableKVState<EntityNumPair, TokenRelation> readableTokenRelState;

    protected ReadableTokenStore readableTokenStore;

    protected ReadableAccountStore readableAccountStore;
    protected ReadableTokenRelationStore readableTokenRelStore;

    protected Token fungibleToken;
    protected Token nonFungibleToken;
    protected Account account;
    protected TokenRelation fungibleTokenRelation;
    protected TokenRelation nonFungibleTokenRelation;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected Account deleteAccount;

    @Mock
    protected Account transferAccount;

    @BeforeEach
    void commonSetUp() {
        givenValidAccount(false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        refreshStoresWithEntitiesOnlyInReadable();
    }

    protected void refreshStoresWithEntitiesOnlyInReadable() {
        givenAccountsInReadableStore();
        givenTokensInReadableStore();
        givenReadableTokenRelsStore();
    }

    private void givenAccountsInReadableStore() {
        readableAccounts = readableAccountState();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
    }

    private void givenTokensInReadableStore() {
        readableTokenState = readableTokenState();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
    }

    private void givenReadableTokenRelsStore() {
        readableTokenRelState = emptyReadableTokenRelsStateBuilder()
                .value(fungiblePair, fungibleTokenRelation)
                .value(nonFungiblePair, nonFungibleTokenRelation)
                .build();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);
    }

    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccountState() {
        return emptyReadableAccountStateBuilder()
                .value(accountEntityNumVirtualKey, account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumVirtualKey, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumPair, TokenRelation> emptyReadableTokenRelsStateBuilder() {
        return MapReadableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapReadableKVState<EntityNum, Token> readableTokenState() {
        return MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                .value(fungibleTokenNum, fungibleToken)
                .value(nonFungibleTokenNum, nonFungibleToken)
                .build();
    }

    protected void givenValidAccount(
            boolean isDeleted,
            @Nullable List<AccountCryptoAllowance> cryptoAllowances,
            @Nullable List<AccountApprovalForAllAllowance> approveForAllNftAllowances,
            @Nullable List<AccountFungibleTokenAllowance> tokenAllowances) {
        account = new Account(
                accountNum,
                alias.alias(),
                null, //  key,
                1_234_567L,
                payerBalance,
                "testAccount",
                isDeleted,
                1_234L,
                1_234_568L,
                0,
                true,
                true,
                3,
                2,
                1,
                2,
                10,
                1,
                3,
                false,
                2,
                0,
                1000L,
                2,
                72000,
                0,
                cryptoAllowances,
                approveForAllNftAllowances,
                tokenAllowances,
                2,
                false,
                null);
    }
}
