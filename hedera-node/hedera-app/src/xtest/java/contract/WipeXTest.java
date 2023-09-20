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

package contract;

import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.addErc721Relation;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import com.hedera.node.app.spi.state.ReadableKVState;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises wipeTokenAccount and wipeTokenAccountNFT via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Wipe {@code ERC721_TOKEN} serial 1234 from Owner's account and verify successful operation</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV1 and verify successful operation</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV2 and verify successful operation</li>*
 *     <li>Via {@code assertExpectedAccounts} verify that owner's nft supply was decreased by 1.</li>*
 *     <li>Via {@code assertExpectedTokenRelations} verify that owner's token balance was decreased by 20.</li>*
 * </ol>
 */
public class WipeXTest extends AbstractContractXTest {

    public static final int NUMBER_OWNED_NFTS = 3;
    public static final long TOKEN_BALANCE = 50L;

    @Override
    protected void doScenarioOperations() {
        // WIPE NFT from OWNER's account
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, new long[] {SN_1234.serialNumber()})
                        .array()),
                assertSuccess());

        // WIPE 10 Tokens via wipeTokenAccountV1
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V1
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                assertSuccess());

        // WIPE 10 Tokens via wipeTokenAccountV2
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                assertSuccess());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .wipeKey(AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .wipeKey(AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc721Relation(tokenRelationships, OWNER_ID, NUMBER_OWNED_NFTS);
        addErc20Relation(tokenRelationships, OWNER_ID, TOKEN_BALANCE);
        return tokenRelationships;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        nfts.put(
                SN_1234,
                Nft.newBuilder()
                        .nftId(SN_1234)
                        .ownerId(OWNER_ID)
                        .spenderId(APPROVED_ID)
                        .metadata(SN_1234_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .numberOwnedNfts(NUMBER_OWNED_NFTS)
                        .alias(OWNER_ADDRESS)
                        .build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder().accountId(UNAUTHORIZED_SPENDER_ID).build());
        return accounts;
    }

    @Override
    protected void assertExpectedAccounts(@NotNull final ReadableKVState<AccountID, Account> accounts) {
        final var account = accounts.get(OWNER_ID);
        assertNotNull(account);
        // Number of owned NFTs should be decreased by 1
        assertEquals(NUMBER_OWNED_NFTS - 1, account.numberOwnedNfts());
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC20_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(tokenRelation);
        // Token balance should be decreased by 20, the sum of the two wipe operations (10 + 10)
        assertEquals(TOKEN_BALANCE - 20L, tokenRelation.balance());
    }
}
