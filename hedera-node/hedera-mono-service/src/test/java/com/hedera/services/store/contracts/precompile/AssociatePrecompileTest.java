/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountMerkleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failInvalidResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidFullPrefix;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiAssociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiDissociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentRecipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile.decodeAssociation;
import static com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile.decodeMultipleAssociations;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.HederaTokenRel;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.AssociateLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class AssociatePrecompileTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private EncodingFacade encoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AssociateLogic associateLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private MessageFrame frame;
    @Mock private MessageFrame parentFrame;
    @Mock private Deque<MessageFrame> frameDeque;
    @Mock private Iterator<MessageFrame> dequeIterator;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private FeeCalculator feeCalculator;
    @Mock private FeeObject mockFeeObject;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes ASSOCIATE_INPUT =
            Bytes.fromHexString(
                    "0x49146bde00000000000000000000000000000000000000000000000000000000000004820000000000000000000000000000000000000000000000000000000000000480");
    private static final Bytes MULTIPLE_ASSOCIATE_INPUT =
            Bytes.fromHexString(
                    "0x2e63879b00000000000000000000000000000000000000000000000000000000000004880000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004860000000000000000000000000000000000000000000000000000000000000486");

    private HTSPrecompiledContract subject;
    private MockedStatic<AssociatePrecompile> associatePrecompile;
    private MockedStatic<MultiAssociatePrecompile> multiAssociatePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenAssociateToAccount,
                Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);

        associatePrecompile = Mockito.mockStatic(AssociatePrecompile.class);
        multiAssociatePrecompile = Mockito.mockStatic(MultiAssociatePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        associatePrecompile.close();
        multiAssociatePrecompile.close();
    }

    @Test
    void computeAssociateTokenFailurePathWorks() {
        // given:
        givenCommonFrameContext();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                false,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                wrappedLedgers))
                .willReturn(false);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidFullPrefix, result);
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenFailurePathWorksWithNullLedgers() {
        // given:
        givenFrameContextWithDelegateCallFromParent();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                false,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                null))
                .willThrow(new NullPointerException());
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failInvalidResult, result);
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithDelegateCall() {
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(contractAddress);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getRecipientAddress()).willReturn(recipientAddress);
        givenLedgers();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                true,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                recipientAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(associateLogic.validateSyntax(any())).willReturn(OK);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(accountMerkleId),
                        Collections.singletonList(tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenBadSyntax() {
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(contractAddress);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getRecipientAddress()).willReturn(recipientAddress);
        givenLedgers();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                true,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                recipientAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(associateLogic.validateSyntax(any())).willReturn(TOKEN_ID_REPEATED_IN_TOKEN_LIST);
        given(creator.createUnsuccessfulSyntheticRecord(TOKEN_ID_REPEATED_IN_TOKEN_LIST))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        subject.computeInternal(frame);

        verify(wrappedLedgers, never()).commit();
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithDelegateCallFromParentFrame() {
        givenFrameContextWithDelegateCallFromParent();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                true,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(accountMerkleId),
                        Collections.singletonList(tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithEmptyMessageFrameStack() {
        givenFrameContextWithEmptyMessageFrameStack();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                false,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(accountMerkleId),
                        Collections.singletonList(tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithoutParentFrame() {
        givenFrameContextWithoutParentFrame();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKEN);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        associatePrecompile
                .when(() -> decodeAssociation(eq(pretendArguments), any()))
                .thenReturn(associateOp);
        given(syntheticTxnFactory.createAssociate(associateOp)).willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                false,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(associateLogic)
                .associate(
                        Id.fromGrpcAccount(accountMerkleId),
                        Collections.singletonList(tokenMerkleId));
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void computeMultiAssociateTokenHappyPathWorks() {
        givenCommonFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = Bytes.ofUnsignedInt(ABI_ID_ASSOCIATE_TOKENS);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(eq(pretendArguments), any()))
                .thenReturn(multiAssociateOp);
        given(syntheticTxnFactory.createAssociate(multiAssociateOp))
                .willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveKey(
                                false,
                                Id.fromGrpcAccount(accountMerkleId).asEvmAddress(),
                                senderAddress,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newAssociateLogic(accountStore, tokenStore))
                .willReturn(associateLogic);
        given(associateLogic.validateSyntax(any())).willReturn(OK);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        verify(associateLogic)
                .associate(Id.fromGrpcAccount(accountMerkleId), multiAssociateOp.tokenIds());
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateTokens() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(any(), any()))
                .thenReturn(multiAssociateOp);
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(multiDissociateOp.accountId());
        builder.addAllTokens(multiDissociateOp.tokenIds());
        given(syntheticTxnFactory.createAssociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateToken() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(associateOp.accountId());
        builder.addAllTokens(associateOp.tokenIds());
        given(syntheticTxnFactory.createAssociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeAssociateToken() {
        associatePrecompile
                .when(() -> decodeAssociation(ASSOCIATE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeAssociation(ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
    }

    @Test
    void decodeMultipleAssociateToken() {
        multiAssociatePrecompile
                .when(() -> decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertEquals(2, decodedInput.tokenIds().size());
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
        assertTrue(decodedInput.tokenIds().get(1).getTokenNum() > 0);
    }

    private void givenFrameContextWithDelegateCallFromParent() {
        given(parentFrame.getContractAddress()).willReturn(parentContractAddress);
        given(parentFrame.getRecipientAddress()).willReturn(parentRecipientAddress);
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(true);
        given(frame.getMessageFrameStack().iterator().next()).willReturn(parentFrame);
    }

    private void givenFrameContextWithEmptyMessageFrameStack() {
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(false);
    }

    private void givenFrameContextWithoutParentFrame() {
        givenCommonFrameContext();
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(true, false);
    }

    private void givenCommonFrameContext() {
        given(frame.getContractAddress()).willReturn(contractAddress);
        given(frame.getRecipientAddress()).willReturn(contractAddress);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(frame.getMessageFrameStack()).willReturn(frameDeque);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }
}
