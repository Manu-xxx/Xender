/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForNonFungibleToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSerialNumberResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddressConvertedToContractId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payerAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payerId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payerIdConvertedToAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderIdConvertedToAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenAddressConvertedToEntityId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.services.store.contracts.precompile.TokenKeyType.FREEZE_KEY;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee.FeeType;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.state.submerkle.FractionalFeeSpec;
import com.hedera.services.state.submerkle.RoyaltyFeeSpec;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.FixedFee;
import com.hedera.services.store.contracts.precompile.codec.FractionalFee;
import com.hedera.services.store.contracts.precompile.codec.FungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import com.hedera.services.store.contracts.precompile.codec.NonFungibleTokenInfo;
import com.hedera.services.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenKey;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
class GetTokenInfoPrecompilesTest {

    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private DecodingFacade decoder;
    @Mock private EncodingFacade encoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private NetworkInfo networkInfo;
    @Mock private FeeObject mockFeeObject;
    @Mock private JKey key;
    @Mock private JContractIDKey contractKey;
    @Mock private JDelegatableContractIDKey delegateContractKey;
    @Mock private FcCustomFee customFixedFee;
    @Mock private FcCustomFee customFractionalFee;
    @Mock private FcCustomFee customRoyaltyFee;
    @Mock private FixedFeeSpec fixedFeeSpec;
    @Mock private FractionalFeeSpec fractionalFeeSpec;
    @Mock private RoyaltyFeeSpec royaltyFeeSpec;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;

    // Common token properties
    private final String name = "Name";
    private final String symbol = "N";
    private final EntityId treasury = senderId;
    private final Address treasuryAddress = senderIdConvertedToAddress;
    private final String memo = "Memo";
    private final long maxSupply = 10000;
    private final long totalSupply = 20500;
    private final boolean deleted = false;
    private final boolean defaultKycStatus = false;
    private final boolean pauseStatus = false;
    private final String ledgerId = "0x03";

    // Key properties
    private final boolean freezeDefault = false;
    private final KeyValue keyValue =
            new KeyValue(false, parentContractAddress, new byte[] {}, new byte[] {}, null);
    private final TokenKey tokenKey = new TokenKey(TokenKeyType.ADMIN_KEY.value(), keyValue);
    private final List<TokenKey> tokenKeys = new ArrayList<>();

    // Expiry properties
    private final long expiryPeriod = 10200L;
    private final EntityId autoRenewAccount = senderId;
    private final Address autoRenewAccountAddress = senderIdConvertedToAddress;
    private final long autoRenewPeriod = 500L;

    // Fungible info properties
    private final int decimals = 10;

    // Non-fungible info properties
    private final long serialNumber = 1;
    private final Address ownerId = payerIdConvertedToAddress;
    private final long creationTime = 152435353252L;
    private final String metadata = "Metadata";
    private final Address spenderId = senderIdConvertedToAddress;

    // Fee properties
    private final Address feeCollector = payerIdConvertedToAddress;
    private final Address feeToken = tokenAddress;
    private final EntityId feeTokenEntityId = tokenAddressConvertedToEntityId;
    private final EntityId feeCollectorEntityId = payerId;
    private final long amount = 100L;
    private final long numerator = 1L;
    private final long denominator = 2L;
    private final long minimumAmount = 10_000L;
    private final long maximumAmount = 400_000_000L;
    private final boolean isNetOfTransfers = false;

    // Info objects
    private final Expiry expiry = new Expiry(expiryPeriod, autoRenewAccountAddress, autoRenewPeriod);
    private TokenInfo tokenInfo;
    private FungibleTokenInfo fungibleTokenInfo;
    private NonFungibleTokenInfo nonFungibleTokenInfo;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);

        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(tokenMerkleId))
                .thenReturn(tokenMerkleAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(treasury))
                .thenReturn(treasuryAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(payerId))
                .thenReturn(payerIdConvertedToAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(senderId))
                .thenReturn(senderIdConvertedToAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(feeTokenEntityId))
                .thenReturn(feeToken);

        tokenKeys.add(tokenKey);
        tokenInfo = createTokenInfo(tokenKeys, true, false);
        fungibleTokenInfo = new FungibleTokenInfo(tokenInfo, decimals);
        nonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        tokenInfo, serialNumber, ownerId, creationTime, metadata, spenderId);

        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        decoder,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory,
                        networkInfo);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
    }

    @Test
    void getTokenInfoWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalKeyContext();

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWorksWithContractKey() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.INFINITE);
        givenKeyContext(key, FREEZE_KEY);

        final var tokenKeys = new ArrayList<TokenKey>();
        final var tokenKey =
                new TokenKey(
                        FREEZE_KEY.value(),
                        new KeyValue(
                                false, parentContractAddress, new byte[] {}, new byte[] {}, null));
        tokenKeys.add(tokenKey);
        tokenInfo = createTokenInfo(tokenKeys, false, false);

        given(key.getEd25519()).willReturn(new byte[] {});
        given(key.getECDSASecp256k1Key()).willReturn(new byte[] {});
        given(key.getContractIDKey()).willReturn(contractKey);
        given(key.getDelegatableContractIdKey()).willReturn(null);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.asTypedEvmAddress(
                                        parentContractAddressConvertedToContractId))
                .thenReturn(parentContractAddress);
        given(contractKey.getContractID()).willReturn(parentContractAddressConvertedToContractId);

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWorksWithDelegatableContractKey() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.INFINITE);
        givenKeyContext(key, FREEZE_KEY);

        final var tokenKeys = new ArrayList<TokenKey>();
        final var tokenKey =
                new TokenKey(
                        FREEZE_KEY.value(),
                        new KeyValue(
                                false, null, new byte[] {}, new byte[] {}, parentContractAddress));
        tokenKeys.add(tokenKey);
        tokenInfo = createTokenInfo(tokenKeys, false, false);

        given(key.getEd25519()).willReturn(new byte[] {});
        given(key.getECDSASecp256k1Key()).willReturn(new byte[] {});
        given(key.getContractIDKey()).willReturn(null);
        given(key.getDelegatableContractIdKey()).willReturn(delegateContractKey);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.asTypedEvmAddress(
                                        parentContractAddressConvertedToContractId))
                .thenReturn(parentContractAddress);
        given(delegateContractKey.getContractID())
                .willReturn(parentContractAddressConvertedToContractId);

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithAllKeyTypesWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);

        final TokenKey adminKey = new TokenKey(TokenKeyType.ADMIN_KEY.value(), keyValue);
        final TokenKey kycKey = new TokenKey(TokenKeyType.KYC_KEY.value(), keyValue);
        final TokenKey freezeKey = new TokenKey(FREEZE_KEY.value(), keyValue);
        final TokenKey wipeKey = new TokenKey(TokenKeyType.WIPE_KEY.value(), keyValue);
        final TokenKey supplyKey = new TokenKey(TokenKeyType.SUPPLY_KEY.value(), keyValue);
        final TokenKey feeScheduleKey =
                new TokenKey(TokenKeyType.FEE_SCHEDULE_KEY.value(), keyValue);
        final TokenKey pauseKey = new TokenKey(TokenKeyType.PAUSE_KEY.value(), keyValue);
        final List<TokenKey> tokenKeys = new ArrayList<>();
        tokenKeys.add(adminKey);
        tokenKeys.add(kycKey);
        tokenKeys.add(freezeKey);
        tokenKeys.add(wipeKey);
        tokenKeys.add(supplyKey);
        tokenKeys.add(feeScheduleKey);
        tokenKeys.add(pauseKey);
        tokenInfo = createTokenInfo(tokenKeys, true, false);

        givenKeyContextAllKeysActive(key);
        givenMinimalKeyContext();

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        given(wrappedLedgers.decimalsOf(tokenMerkleId)).willReturn(decimals);
        givenMinimalKeyContext();

        given(encoder.encodeGetFungibleTokenInfo(fungibleTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper =
                createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                        Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalUniqueTokenContext();
        givenMinimalKeyContext();

        given(encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFixedFeeWithDenominationWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenFixedFeeContextWithDenomination();
        givenMinimalKeyContext();

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFixedFeeWithoutDenominationWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenFixedFeeContextWithoutDenomination();
        givenMinimalKeyContext();

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoWithFractionalFeeWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenFractionalFeeContext();
        givenMinimalKeyContext();

        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWithRoyaltyFeeWithFallbackFeeWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper =
                createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                        Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalUniqueTokenContext();
        givenRoyaltyFeeContext(true);
        givenMinimalKeyContext();

        given(encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoWithRoyaltyFeeWithoutFallbackFeeWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper =
                createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                        Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalUniqueTokenContext();
        givenRoyaltyFeeContext(false);
        givenMinimalKeyContext();

        given(encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoOfMissingTokenFails() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);
        given(wrappedLedgers.nameOf(tokenMerkleId)).willThrow(new InvalidTransactionException(ResponseCodeEnum.INVALID_TOKEN_ID));

        givenMinimalContextForInvalidTokenIdCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoOfMissingTokenFails() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        given(wrappedLedgers.nameOf(tokenMerkleId)).willThrow(new InvalidTransactionException(ResponseCodeEnum.INVALID_TOKEN_ID));

        givenMinimalContextForInvalidTokenIdCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoOfMissingSerialNumberFails() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper =
                createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                        Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        final var nftId = NftId.fromGrpc(tokenMerkleId, serialNumber);
        given(wrappedLedgers.ownerOf(nftId)).willThrow(new InvalidTransactionException(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER));

        givenMinimalContextForInvalidNftSerialNumberCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidSerialNumberResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        given(wrappedLedgers.isDeleted(tokenMerkleId)).willReturn(true);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalKeyContext();

        tokenInfo = createTokenInfo(tokenKeys, true, true);
        given(encoder.encodeGetTokenInfo(tokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getFungibleTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper = createTokenInfoWrapperForToken(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        given(decoder.decodeGetFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        given(wrappedLedgers.isDeleted(tokenMerkleId)).willReturn(true);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        given(wrappedLedgers.decimalsOf(tokenMerkleId)).willReturn(decimals);
        givenMinimalKeyContext();

        fungibleTokenInfo = new FungibleTokenInfo(createTokenInfo(tokenKeys, true, true), decimals);
        given(encoder.encodeGetFungibleTokenInfo(fungibleTokenInfo)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getNonFungibleTokenInfoOfDeletedTokenWorks() {
        givenMinimalFrameContext();

        final var tokenInfoWrapper =
                createTokenInfoWrapperForNonFungibleToken(tokenMerkleId, serialNumber);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId),
                        Bytes.wrap(new byte[] {Long.valueOf(serialNumber).byteValue()}));
        given(decoder.decodeGetNonFungibleTokenInfo(pretendArguments)).willReturn(tokenInfoWrapper);

        givenMinimalTokenContext(TokenSupplyType.FINITE);
        given(wrappedLedgers.isDeleted(tokenMerkleId)).willReturn(true);
        givenKeyContext(key, TokenKeyType.ADMIN_KEY);
        givenMinimalUniqueTokenContext();
        givenMinimalKeyContext();

        nonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        createTokenInfo(tokenKeys, true, true),
                        serialNumber,
                        ownerId,
                        creationTime,
                        metadata,
                        spenderId);
        given(encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo))
                .willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    private void givenReadOnlyFeeSchedule() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
    }

    private void givenMinimalKeyContext() {
        given(key.getEd25519()).willReturn(new byte[] {});
        given(key.getECDSASecp256k1Key()).willReturn(new byte[] {});
        given(key.getContractIDKey()).willReturn(contractKey);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.asTypedEvmAddress(
                                        parentContractAddressConvertedToContractId))
                .thenReturn(parentContractAddress);
        given(contractKey.getContractID()).willReturn(parentContractAddressConvertedToContractId);
        given(key.getDelegatableContractIdKey()).willReturn(delegateContractKey);
        given(delegateContractKey.getContractID()).willReturn(null);
    }

    private void givenMinimalTokenContext(final TokenSupplyType tokenSupplyType) {
        given(wrappedLedgers.nameOf(tokenMerkleId)).willReturn(name);
        given(wrappedLedgers.symbolOf(tokenMerkleId)).willReturn(symbol);
        given(wrappedLedgers.treasury(tokenMerkleId)).willReturn(treasury);
        given(wrappedLedgers.memo(tokenMerkleId)).willReturn(memo);
        given(wrappedLedgers.supplyType(tokenMerkleId)).willReturn(tokenSupplyType);
        given(wrappedLedgers.maxSupply(tokenMerkleId)).willReturn(maxSupply);
        given(wrappedLedgers.expiry(tokenMerkleId)).willReturn(expiryPeriod);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(autoRenewAccount))
                .thenReturn(autoRenewAccountAddress);
        given(wrappedLedgers.autoRenewAccount(tokenMerkleId)).willReturn(autoRenewAccount);
        given(wrappedLedgers.autoRenewPeriod(tokenMerkleId)).willReturn(autoRenewPeriod);
        given(wrappedLedgers.totalSupplyOf(tokenMerkleId)).willReturn(totalSupply);
        given(wrappedLedgers.isDeleted(tokenMerkleId)).willReturn(deleted);
        given(wrappedLedgers.accountsKycGrantedByDefault(tokenMerkleId)).willReturn(defaultKycStatus);
        given(wrappedLedgers.isPaused(tokenMerkleId)).willReturn(pauseStatus);

        given(networkInfo.ledgerId()).willReturn(ByteString.copyFrom(unhex(ledgerId.substring(2))));
    }

    private void givenKeyContext(final JKey key, final TokenKeyType keyType) {
        switch (keyType) {
            case ADMIN_KEY -> {
                given(wrappedLedgers.adminKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case KYC_KEY -> {
                given(wrappedLedgers.kycKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case FREEZE_KEY -> {
                given(wrappedLedgers.freezeKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case WIPE_KEY -> {
                given(wrappedLedgers.wipeKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case SUPPLY_KEY -> {
                given(wrappedLedgers.supplyKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case FEE_SCHEDULE_KEY -> {
                given(wrappedLedgers.feeScheduleKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
            case PAUSE_KEY -> {
                given(wrappedLedgers.pauseKey(tokenMerkleId)).willReturn(Optional.of(key));
            }
        }
    }

    private void givenKeyContextAllKeysActive(final JKey key) {
        given(wrappedLedgers.adminKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.kycKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.freezeKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.wipeKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.supplyKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.feeScheduleKey(tokenMerkleId)).willReturn(Optional.of(key));
        given(wrappedLedgers.pauseKey(tokenMerkleId)).willReturn(Optional.of(key));
    }

    private void givenMinimalUniqueTokenContext() {
        final var nftId = NftId.fromGrpc(tokenMerkleId, serialNumber);
        given(wrappedLedgers.ownerOf(nftId)).willReturn(payerAddress);
        given(wrappedLedgers.packedCreationTimeOf(nftId)).willReturn(creationTime);
        given(wrappedLedgers.metadataOf(nftId)).willReturn(metadata);
        given(wrappedLedgers.spenderOf(nftId)).willReturn(senderId);
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenMinimalContextForSuccessfulCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenMinimalContextForInvalidNftSerialNumberCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER))
                .willReturn(mockRecordBuilder);
    }

    private void givenMinimalContextForInvalidTokenIdCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.INVALID_TOKEN_ID))
                .willReturn(mockRecordBuilder);
    }

    private void givenFixedFeeContextWithDenomination() {
        final var fixedFee = new FixedFee(amount, feeToken, false, false, feeCollector);
        final List<FixedFee> fixedFees = new ArrayList<>();
        fixedFees.add(fixedFee);
        tokenInfo = createTokenInfo(fixedFees, new ArrayList<>(), new ArrayList<>(), tokenKeys);

        given(customFixedFee.getFeeCollector()).willReturn(feeCollectorEntityId);
        given(customFixedFee.getFeeType()).willReturn(FeeType.FIXED_FEE);
        given(customFixedFee.getFixedFeeSpec()).willReturn(fixedFeeSpec);
        given(fixedFeeSpec.getUnitsToCollect()).willReturn(amount);
        given(fixedFeeSpec.getTokenDenomination()).willReturn(feeTokenEntityId);
        final List<FcCustomFee> customFees = new ArrayList<>();
        customFees.add(customFixedFee);
        given(wrappedLedgers.feeSchedule(tokenMerkleId)).willReturn(customFees);
    }

    private void givenFixedFeeContextWithoutDenomination() {
        final var fixedFee =
                new FixedFee(
                        amount, Address.wrap(Bytes.wrap(new byte[20])), true, false, feeCollector);
        final List<FixedFee> fixedFees = new ArrayList<>();
        fixedFees.add(fixedFee);
        tokenInfo = createTokenInfo(fixedFees, new ArrayList<>(), new ArrayList<>(), tokenKeys);

        given(customFixedFee.getFeeCollector()).willReturn(feeCollectorEntityId);
        given(customFixedFee.getFeeType()).willReturn(FeeType.FIXED_FEE);
        given(customFixedFee.getFixedFeeSpec()).willReturn(fixedFeeSpec);
        given(fixedFeeSpec.getUnitsToCollect()).willReturn(amount);
        given(fixedFeeSpec.getTokenDenomination()).willReturn(null);
        final List<FcCustomFee> customFees = new ArrayList<>();
        customFees.add(customFixedFee);
        given(wrappedLedgers.feeSchedule(tokenMerkleId)).willReturn(customFees);
    }

    private void givenFractionalFeeContext() {
        final var fractionalFee =
                new FractionalFee(
                        numerator,
                        denominator,
                        minimumAmount,
                        maximumAmount,
                        isNetOfTransfers,
                        feeCollector);
        final List<FractionalFee> fractionalFees = new ArrayList<>();
        fractionalFees.add(fractionalFee);
        tokenInfo =
                createTokenInfo(new ArrayList<>(), fractionalFees, new ArrayList<>(), tokenKeys);

        given(customFractionalFee.getFeeCollector()).willReturn(feeCollectorEntityId);
        given(customFractionalFee.getFeeType()).willReturn(FeeType.FRACTIONAL_FEE);
        given(customFractionalFee.getFractionalFeeSpec()).willReturn(fractionalFeeSpec);
        given(fractionalFeeSpec.getNumerator()).willReturn(numerator);
        given(fractionalFeeSpec.getDenominator()).willReturn(denominator);
        given(fractionalFeeSpec.getMinimumAmount()).willReturn(minimumAmount);
        given(fractionalFeeSpec.getMaximumUnitsToCollect()).willReturn(maximumAmount);
        given(fractionalFeeSpec.isNetOfTransfers()).willReturn(isNetOfTransfers);
        final List<FcCustomFee> customFees = new ArrayList<>();
        customFees.add(customFractionalFee);
        given(wrappedLedgers.feeSchedule(tokenMerkleId)).willReturn(customFees);
    }

    private void givenRoyaltyFeeContext(final boolean hasFallbackFee) {
        RoyaltyFee royaltyFee;
        if (hasFallbackFee) {
            royaltyFee =
                    new RoyaltyFee(numerator, denominator, amount, feeToken, false, feeCollector);
        } else {
            royaltyFee = new RoyaltyFee(numerator, denominator, 0L, null, false, feeCollector);
        }
        final List<RoyaltyFee> royaltyFees = new ArrayList<>();
        royaltyFees.add(royaltyFee);

        tokenInfo = createTokenInfo(new ArrayList<>(), new ArrayList<>(), royaltyFees, tokenKeys);
        nonFungibleTokenInfo =
                new NonFungibleTokenInfo(
                        tokenInfo, serialNumber, ownerId, creationTime, metadata, spenderId);
        given(customRoyaltyFee.getFeeCollector()).willReturn(feeCollectorEntityId);
        given(customRoyaltyFee.getFeeType()).willReturn(FeeType.ROYALTY_FEE);
        given(customRoyaltyFee.getRoyaltyFeeSpec()).willReturn(royaltyFeeSpec);
        given(royaltyFeeSpec.numerator()).willReturn(numerator);
        given(royaltyFeeSpec.denominator()).willReturn(denominator);
        if (hasFallbackFee) {
            given(royaltyFeeSpec.hasFallbackFee()).willReturn(true);
            given(royaltyFeeSpec.fallbackFee()).willReturn(fixedFeeSpec);
            given(fixedFeeSpec.getUnitsToCollect()).willReturn(amount);
            given(fixedFeeSpec.getTokenDenomination()).willReturn(feeTokenEntityId);
        }
        final List<FcCustomFee> customFees = new ArrayList<>();
        customFees.add(customRoyaltyFee);
        given(wrappedLedgers.feeSchedule(tokenMerkleId)).willReturn(customFees);
    }

    private TokenInfo createTokenInfo(
            final List<FixedFee> fixedFees,
            final List<FractionalFee> fractionalFees,
            final List<RoyaltyFee> royaltyFees,
            final List<TokenKey> tokenKeys) {
        final HederaToken hederaToken =
                new HederaToken(
                        name,
                        symbol,
                        EntityIdUtils.asTypedEvmAddress(treasury),
                        memo,
                        true,
                        maxSupply,
                        freezeDefault,
                        tokenKeys,
                        expiry);
        return new TokenInfo(
                hederaToken,
                totalSupply,
                deleted,
                defaultKycStatus,
                pauseStatus,
                fixedFees,
                fractionalFees,
                royaltyFees,
                ledgerId);
    }

    private TokenInfo createTokenInfo(
            final List<TokenKey> tokenKeys,
            final boolean tokenSupplyType,
            final boolean isDeleted) {
        final HederaToken hederaToken =
                new HederaToken(
                        name,
                        symbol,
                        EntityIdUtils.asTypedEvmAddress(treasury),
                        memo,
                        tokenSupplyType,
                        maxSupply,
                        freezeDefault,
                        tokenKeys,
                        expiry);
        return new TokenInfo(
                hederaToken,
                totalSupply,
                isDeleted,
                defaultKycStatus,
                pauseStatus,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                ledgerId);
    }
}
