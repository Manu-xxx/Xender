package common;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import contract.AbstractContractXTest;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static common.CommonXTestConstants.SET_OF_TRADITIONAL_RATES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractXTest {
    private static final AccountID[] SOME_NUMERIC_ACCOUNT_IDS = new AccountID[]{
            AccountID.newBuilder().accountNum(242424L).build(),
            AccountID.newBuilder().accountNum(252525L).build(),
            AccountID.newBuilder().accountNum(262626L).build(),
            AccountID.newBuilder().accountNum(313131L).build(),
            AccountID.newBuilder().accountNum(343434L).build(),
            AccountID.newBuilder().accountNum(373737L).build(),
    };
    private int numNamedAccounts = 0;
    protected final Map<String, AccountID> namedAccountIds = new HashMap<>();

    private static final TokenID[] SOME_NUMERIC_TOKEN_IDS = new TokenID[]{
            TokenID.newBuilder().tokenNum(777777L).build(),
            TokenID.newBuilder().tokenNum(888888L).build(),
            TokenID.newBuilder().tokenNum(999999L).build(),
    };
    private int numNamedTokens = 0;
    protected final Map<String, TokenID> namedTokenIds = new HashMap<>();

    @Mock
    protected Metrics metrics;

    @Test
    void scenarioPasses() {
        setupFeeManager();
        setupInitialStates();
        setupExchangeManager();

        doScenarioOperations();

        assertExpectedNfts(finalNfts());
        assertExpectedAliases(finalAliases());
        assertExpectedAccounts(finalAccounts());
        assertExpectedBytecodes(finalBytecodes());
        assertExpectedStorage(finalStorage(), finalAccounts());
        assertExpectedTokenRelations(finalTokenRelations());
    }

    protected abstract BaseScaffoldingComponent component();

    protected abstract void doScenarioOperations();

    protected void answerSingleQuery(
            @NonNull final QueryHandler handler,
            @NonNull final Query query,
            @NonNull final AccountID payerId,
            @NonNull final Consumer<Response> assertions) {
        final var context = component().queryContextFactory().apply(query, payerId);
        assertions.accept(handler.findResponse(context, ResponseHeader.DEFAULT));
    }

    protected void handleAndCommitSingleTransaction(
            @NonNull final TransactionHandler handler,
            @NonNull final TransactionBody txn) {
        handleAndCommitSingleTransaction(handler, txn, ResponseCodeEnum.SUCCESS);
    }

    protected void handleAndCommitSingleTransaction(
            @NonNull final TransactionHandler handler,
            @NonNull final TransactionBody txn,
            @NonNull final ResponseCodeEnum expectedStatus) {
        final var context = component().txnContextFactory().apply(txn);
        handler.handle(context);
        ((SavepointStackImpl) context.savepointStack()).commitFullStack();
        final var recordBuilder = context.recordBuilder(SingleTransactionRecordBuilder.class);
        assertEquals(expectedStatus, recordBuilder.status());
    }

    protected void addNamedAccount(
            @NonNull final String name,
            @NonNull final Map<AccountID, Account> accounts) {
        addNamedAccount(name, b -> {}, accounts);
    }

    protected void addNamedAccount(
            @NonNull final String name,
            @NonNull final Consumer<Account.Builder> spec,
            @NonNull final Map<AccountID, Account> accounts) {
        final var accountId = SOME_NUMERIC_ACCOUNT_IDS[numNamedAccounts++];
        namedAccountIds.put(name, accountId);
        final var builder = Account.newBuilder().accountId(accountId);
        spec.accept(builder);
        accounts.put(accountId, builder.build());
    }

    protected AccountID idOfNamedAccount(@NonNull final String name) {
        return Objects.requireNonNull(namedAccountIds.get(name));
    }

    protected void addNamedFungibleToken(
            @NonNull final String name,
            @NonNull final Consumer<Token.Builder> spec,
            @NonNull final Map<TokenID, Token> tokens) {
        addNamedToken(name, spec.andThen(b -> b.tokenType(TokenType.FUNGIBLE_COMMON)), tokens);
    }

    protected void addNamedNonFungibleToken(
            @NonNull final String name,
            @NonNull final Consumer<Token.Builder> spec,
            @NonNull final Map<TokenID, Token> tokens) {
        addNamedToken(name, spec.andThen(b -> b.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)), tokens);
    }

    protected void addNewRelation(
            @NonNull final String account,
            @NonNull final String token,
            @NonNull final Consumer<TokenRelation.Builder> spec,
            @NonNull final Map<EntityIDPair, TokenRelation> tokenRels) {
        final Consumer<TokenRelation.Builder> defaultSpec = b -> b.kycGranted(true);
        addNewRelation(
                idOfNamedAccount(account),
                idOfNamedToken(token),
                defaultSpec.andThen(spec),
                tokenRels);
    }

    protected void addNewRelation(
            @NonNull final AccountID accountID,
            @NonNull final TokenID tokenID,
            @NonNull final Consumer<TokenRelation.Builder> spec,
            @NonNull final Map<EntityIDPair, TokenRelation> tokenRels) {
        final var builder = TokenRelation.newBuilder()
                .accountId(accountID)
                .tokenId(tokenID);
        spec.accept(builder);
        tokenRels.put(new EntityIDPair(accountID, tokenID), builder.build());
    }

    protected void addNamedToken(
            @NonNull final String name,
            @NonNull final Consumer<Token.Builder> spec,
            @NonNull final Map<TokenID, Token> tokens) {
        final var tokenId = SOME_NUMERIC_TOKEN_IDS[numNamedTokens++];
        namedTokenIds.put(name, tokenId);
        final var builder = Token.newBuilder().tokenId(tokenId);
        spec.accept(builder);
        tokens.put(tokenId, builder.build());
    }

    protected TokenID idOfNamedToken(@NonNull final String name) {
        return Objects.requireNonNull(namedTokenIds.get(name));
    }

    protected long initialEntityNum() {
        // An x-test that doesn't override this can't create entities
        return Long.MAX_VALUE;
    }

    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>();
    }

    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        return new HashMap<>();
    }

    protected Map<NftID, Nft> initialNfts() {
        return new HashMap<>();
    }

    protected Map<FileID, File> initialFiles() {
        return new HashMap<>();
    }

    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>();
    }

    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>();
    }

    protected RunningHashes initialRunningHashes() {
        return RunningHashes.DEFAULT;
    }

    protected Bytes resourceAsBytes(@NonNull final String loc) {
        try {
            try (final var in = AbstractContractXTest.class.getClassLoader().getResourceAsStream(loc)) {
                final var bytes = Objects.requireNonNull(in).readAllBytes();
                return Bytes.wrap(bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected void assertExpectedStorage(
            @NonNull ReadableKVState<SlotKey, SlotValue> storage,
            @NonNull ReadableKVState<AccountID, Account> accounts) {
    }

    protected void assertExpectedNfts(@NonNull ReadableKVState<NftID, Nft> nfts) {
    }

    protected void assertExpectedAliases(@NonNull ReadableKVState<ProtoBytes, AccountID> aliases) {
    }

    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
    }

    protected void assertExpectedAccounts(@NonNull ReadableKVState<AccountID, Account> accounts) {
    }

    protected void assertExpectedBytecodes(@NonNull ReadableKVState<EntityNumber, Bytecode> bytecodes) {
    }

    private ReadableKVState<NftID, Nft> finalNfts() {
        return component()
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.NFTS_KEY);
    }

    private ReadableKVState<ProtoBytes, AccountID> finalAliases() {
        return component()
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ALIASES_KEY);
    }

    private ReadableKVState<SlotKey, SlotValue> finalStorage() {
        return component()
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.STORAGE_KEY);
    }

    private ReadableKVState<EntityNumber, Bytecode> finalBytecodes() {
        return component()
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.BYTECODE_KEY);
    }

    private ReadableKVState<AccountID, Account> finalAccounts() {
        return component()
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
    }

    private ReadableKVState<EntityIDPair, TokenRelation> finalTokenRelations() {
        return component()
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.TOKEN_RELS_KEY);
    }

    private Map<FileID, File> initialFilesWithExchangeRate() {
        final var scenarioFiles = initialFiles();
        scenarioFiles.put(
                FileID.newBuilder().fileNum(112).build(),
                File.newBuilder()
                        .contents(ExchangeRateSet.PROTOBUF.toBytes(SET_OF_TRADITIONAL_RATES))
                        .build());
        return scenarioFiles;
    }

    private void setupFeeManager() {
        var feeScheduleBytes = resourceAsBytes("feeSchedules.bin");
        component().feeManager().update(feeScheduleBytes);
    }

    private void setupExchangeManager() {
        final var state = Objects.requireNonNull(
                component().workingStateAccessor().getHederaState());
        final var midnightRates = state.createReadableStates(FeeService.NAME)
                .<ExchangeRateSet>getSingleton("MIDNIGHT_RATES")
                .get();

        component().exchangeRateManager().init(state, ExchangeRateSet.PROTOBUF.toBytes(midnightRates));
    }

    private void setupInitialStates() {
        final var fakeHederaState = (FakeHederaState) component().hederaState();

        fakeHederaState.addService(
                EntityIdService.NAME, Map.of("ENTITY_ID", new AtomicReference<>(new EntityNumber(initialEntityNum()))));

        fakeHederaState.addService("RecordCache", Map.of("TransactionRecordQueue", new ArrayDeque<>()));

        fakeHederaState.addService(
                FeeService.NAME, Map.of("MIDNIGHT_RATES", new AtomicReference<>(SET_OF_TRADITIONAL_RATES)));

        fakeHederaState.addService(
                BlockRecordService.NAME,
                Map.of(
                        BlockRecordService.BLOCK_INFO_STATE_KEY, new AtomicReference<>(BlockInfo.DEFAULT),
                        BlockRecordService.RUNNING_HASHES_STATE_KEY, new AtomicReference<>(initialRunningHashes())));

        fakeHederaState.addService(
                TokenService.NAME,
                Map.of(
                        TokenServiceImpl.ACCOUNTS_KEY, initialAccounts(),
                        TokenServiceImpl.TOKENS_KEY, initialTokens(),
                        TokenServiceImpl.TOKEN_RELS_KEY, initialTokenRelationships(),
                        TokenServiceImpl.ALIASES_KEY, initialAliases(),
                        TokenServiceImpl.NFTS_KEY, initialNfts()));
        fakeHederaState.addService(
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, initialFilesWithExchangeRate()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));

        component().workingStateAccessor().setHederaState(fakeHederaState);
    }
}
