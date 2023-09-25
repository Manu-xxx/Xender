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
import static contract.MiscClassicTransfersXTestConstants.NEXT_ENTITY_NUM;
import static contract.WipeXTest.NUMBER_OWNED_NFTS;
import static contract.WipeXTest.TOKEN_BALANCE;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.hedera.node.app.spi.state.ReadableKVState;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises mint on a fungible and non-fungible token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Mints {@code ERC20_TOKEN} via MINT operation</li>
 *     <li>Mints {@code ERC20_TOKEN} via MINT_V2 operation</li>
 *     <li>Mints {@code ERC721_TOKEN} via MINT operation</li>
 *     <li>Mints {@code ERC721_TOKEN} via MINT_V2 operation</li>
 * </ol>
 */
public class MintsXTest extends AbstractContractXTest {
    static final long MINT_AMOUNT = 10;

    static final byte[][] metadata = {"data".getBytes()};

    @Override
    protected void doScenarioOperations() {
        // Mint 10 Tokens via mintV1
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, BigInteger.valueOf(MINT_AMOUNT), new byte[][] {})
                        .array()),
                assertSuccess());

        // Mint 10 Tokens via mintV2
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, MINT_AMOUNT, new byte[][] {})
                        .array()),
                assertSuccess());

        // Mint NFT via mintV1
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, BigInteger.ZERO, metadata)
                        .array()),
                assertSuccess());

        // Mint NFT via mintV2
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, 0L, metadata)
                        .array()),
                assertSuccess());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
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
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, TOKEN_BALANCE);
        addErc721Relation(tokenRelationships, OWNER_ID, NUMBER_OWNED_NFTS);
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
        return accounts;
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC20_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(tokenRelation);

        final var nftRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(nftRelation);

        // Token balance should be increased by 20, the sum of the two mint operations (10 + 10)
        assertEquals(TOKEN_BALANCE + (MINT_AMOUNT + MINT_AMOUNT), tokenRelation.balance());

        assertEquals(NUMBER_OWNED_NFTS + 2, nftRelation.balance());
    }
}
