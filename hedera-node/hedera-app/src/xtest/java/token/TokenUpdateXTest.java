/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package token;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;

public class TokenUpdateXTest extends AbstractTokenUpdateXTest {

    protected static final String TOKEN_ID = "tokenId";

    private Map<AccountID, Account> accountsCurrent;

    private static final String FIRST_ROYALTY_COLLECTOR = "firstRoyaltyCollector";
    private static final int PLENTY_OF_SLOTS = 10;

    private static final String TOKEN_TREASURY = "tokenTreasury";
    private static final long INITIAL_SUPPLY = 123456789;

    private final Key TOKEN_TREASURY_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaab00aaa00aaa00aaa00aaa00aaa00aaaad"))
            .build();

    private static final Key DEFAULT_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();

    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .freezeKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .kycKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .wipeKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .supplyKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .feeScheduleKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .pauseKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
        handleAndCommitSingleTransaction(
                component.tokenUpdateHandler(),
                TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder()
                                .accountID(idOfNamedAccount(TOKEN_TREASURY))
                                .build())
                        .tokenUpdate(TokenUpdateTransactionBody.newBuilder()
                                .token(idOfNamedToken(TOKEN_ID))
                                .adminKey(IMMUTABILITY_SENTINEL_KEY))
                        .build(),
                OK);
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        ((ReadableKVStateBase<TokenID, Token>) tokens).reset();
        final var token = Objects.requireNonNull(tokens.get(idOfNamedToken(TOKEN_ID)));
        assertEquals(null, token.freezeKey());
        assertEquals(null, token.adminKey());
        assertEquals(null, token.kycKey());
        assertEquals(null, token.wipeKey());
        assertEquals(null, token.supplyKey());
        assertEquals(null, token.feeScheduleKey());
        assertEquals(null, token.pauseKey());
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        /**
         * Initial token should be initialized with all keys to test removal. Set DEFAULT_KEY as default value for all keys.
         */
        final var tokens = super.initialTokens();
        addNamedFungibleToken(
                TOKEN_ID,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .adminKey(DEFAULT_KEY)
                        .freezeKey(DEFAULT_KEY)
                        .supplyKey(DEFAULT_KEY)
                        .kycKey(DEFAULT_KEY)
                        .feeScheduleKey(DEFAULT_KEY)
                        .wipeKey(DEFAULT_KEY)
                        .pauseKey(DEFAULT_KEY),
                tokens);
        return tokens;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(
                TOKEN_TREASURY, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS).key(TOKEN_TREASURY_KEY), accounts);
        addNamedAccount(FIRST_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        accountsCurrent = accounts;
        return accounts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = super.initialTokenRelationships();
        addNewRelation(TOKEN_TREASURY, TOKEN_ID, b -> b.balance(INITIAL_SUPPLY), tokenRels);
        return tokenRels;
    }
}
