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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.AssortedOpsXTestConstants.ONE_HBAR;
import static contract.MiscViewsXTestConstants.COINBASE_ID;
import static contract.MiscViewsXTestConstants.ERC20_TOKEN_ID;
import static contract.MiscViewsXTestConstants.ERC721_TOKEN_ID;
import static contract.MiscViewsXTestConstants.ERC_USER_ADDRESS;
import static contract.MiscViewsXTestConstants.ERC_USER_ID;
import static contract.MiscViewsXTestConstants.GET_PRNG_SEED;
import static contract.MiscViewsXTestConstants.NEXT_ENTITY_NUM;
import static contract.MiscViewsXTestConstants.OPERATOR_ID;
import static contract.MiscViewsXTestConstants.PRNG_SEED;
import static contract.MiscViewsXTestConstants.SECRET;
import static contract.MiscViewsXTestConstants.SPECIAL_QUERIES_X_TEST;
import static contract.MiscViewsXTestConstants.VIEWS_INITCODE_FILE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class MiscViewsXTest extends AbstractContractXTest {

    @Override
    protected void handleAndCommitScenarioTransactions() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        final var context =
                handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCallHandler(), synthCallPrng());
        final var recordBuilder = context.recordBuilder(SingleTransactionRecordBuilder.class);
        assertEquals(SUCCESS, recordBuilder.status());
    }

    private TransactionBody synthCreateTxn() {
        final var params =
                Bytes.wrap(TupleType.parse("(uint256)").encodeElements(SECRET).array());
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ERC_USER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .constructorParameters(params)
                        .fileID(VIEWS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthCallPrng() {
        return createCallTransactionBody(ERC_USER_ID, 0L, SPECIAL_QUERIES_X_TEST, GET_PRNG_SEED.encodeCallWithArgs());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                VIEWS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/SpecialQueriesXTest.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<Bytes, AccountID> initialAliases() {
        final var aliases = new HashMap<Bytes, AccountID>();
        aliases.put(ERC_USER_ADDRESS, ERC_USER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                ERC_USER_ID,
                Account.newBuilder()
                        .accountId(ERC_USER_ID)
                        .alias(ERC_USER_ADDRESS)
                        .tinybarBalance(100 * ONE_HBAR)
                        .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                                .tokenId(ERC721_TOKEN_ID)
                                .spenderId(OPERATOR_ID)
                                .build()))
                        .build());
        accounts.put(
                OPERATOR_ID,
                Account.newBuilder()
                        .accountId(OPERATOR_ID)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        for (long sn = 1; sn <= 3; sn++) {
            final var id =
                    NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(sn).build();
            nfts.put(
                    id,
                    Nft.newBuilder()
                            .nftId(id)
                            .ownerId(ERC_USER_ID)
                            .metadata(Bytes.wrap("https://example.com/721/" + sn))
                            .build());
        }
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .balance(111L)
                        .kycGranted(true)
                        .build());
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .balance(3L)
                        .kycGranted(true)
                        .build());
        return tokenRelationships;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        final var erc20Symbol = "SYM20";
        final var erc20Name = "20 Coin";
        final var erc20Memo = "20 Coin Memo";
        final var erc20Decimals = 2;
        final var erc20TotalSupply = 888L;
        final var erc721Symbol = "SYM721";
        final var erc721Name = "721 Unique Things";
        final var erc721Memo = "721 Unique Things Memo";
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .memo(erc20Memo)
                        .name(erc20Name)
                        .symbol(erc20Symbol)
                        .decimals(erc20Decimals)
                        .totalSupply(erc20TotalSupply)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .memo(erc721Memo)
                        .name(erc721Name)
                        .symbol(erc721Symbol)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected RunningHashes initialRunningHashes() {
        return RunningHashes.newBuilder().nMinus3RunningHash(PRNG_SEED).build();
    }

    @Override
    protected void assertExpectedStorage(
            @NotNull ReadableKVState<SlotKey, SlotValue> storage,
            @NotNull ReadableKVState<AccountID, Account> accounts) {}

    @Override
    protected void assertExpectedAliases(@NotNull ReadableKVState<ProtoBytes, AccountID> aliases) {}

    @Override
    protected void assertExpectedAccounts(@NotNull ReadableKVState<AccountID, Account> accounts) {}

    @Override
    protected void assertExpectedBytecodes(@NotNull ReadableKVState<EntityNumber, Bytecode> bytecodes) {}
}
