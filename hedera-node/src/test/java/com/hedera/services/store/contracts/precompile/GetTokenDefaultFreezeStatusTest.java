package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.defaultFreezeStatusWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTokenDefaultFreezeStatusTest {
  @Mock private GlobalDynamicProperties dynamicProperties;
  @Mock private GasCalculator gasCalculator;
  @Mock private MessageFrame frame;
  @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
  @Mock private RecordsHistorian recordsHistorian;
  @Mock private DecodingFacade decoder;
  @Mock private EncodingFacade encoder;
  @Mock private SyntheticTxnFactory syntheticTxnFactory;
  @Mock private ExpiringCreations creator;
  @Mock private SideEffectsTracker sideEffects;
  @Mock private ImpliedTransfersMarshal impliedTransfers;
  @Mock private FeeCalculator feeCalculator;
  @Mock private StateView stateView;
  @Mock private HederaStackedWorldStateUpdater worldUpdater;
  @Mock private WorldLedgers wrappedLedgers;
  @Mock private UsagePricesProvider resourceCosts;
  @Mock private HbarCentExchange exchange;
  @Mock private TransactionBody.Builder mockSynthBodyBuilder;
  @Mock private InfrastructureFactory infrastructureFactory;

  @Mock
  private AssetsLoader assetLoader;

  private HTSPrecompiledContract subject;

  @BeforeEach
  void setUp() throws IOException {
    PrecompilePricingUtils precompilePricingUtils =
        new PrecompilePricingUtils(
            assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
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
            impliedTransfers,
            () -> feeCalculator,
            stateView,
            precompilePricingUtils,
            infrastructureFactory);
  }

  @Test
  void getTokenDefaultFreezeStatus() {
    final var output =
        "0x000000000000000000000000000000000000000000000000000000000000"
            + "00160000000000000000000000000000000000000000000000000000000000000001";

    final var successOutput =
        Bytes.fromHexString(
            "0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                + "00000000000000000000000000000000000000000000000000001");

    givenMinimalFrameContext();
    givenLedgers();
    givenMinimalContextForSuccessfulCall();
    Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS));

    given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
        .willReturn(mockSynthBodyBuilder);
    given(decoder.decodeTokenDefaultFreezeStatus(any())).willReturn(defaultFreezeStatusWrapper);
    given(encoder.encodeGetTokenDefaultFreezeStatus(true)).willReturn(successResult);
    given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
    given(wrappedLedgers.defaultFreezeStatus((any()))).willReturn(Boolean.TRUE);
    given(encoder.encodeGetTokenDefaultFreezeStatus(true)).willReturn(Bytes.fromHexString(output));
    given(frame.getValue()).willReturn(Wei.ZERO);

    // when
    subject.prepareFields(frame);
    subject.prepareComputation(pretendArguments, a -> a);
    final var result = subject.computeInternal(frame);

    // then
    assertEquals(successOutput, result);
  }

  private void givenMinimalContextForSuccessfulCall() {
    Optional<WorldUpdater> parent = Optional.of(worldUpdater);
    given(worldUpdater.parentUpdater()).willReturn(parent);
    given(worldUpdater.permissivelyUnaliased(any()))
        .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
  }

  private void givenMinimalFrameContext() {
    given(frame.getSenderAddress()).willReturn(contractAddress);
    given(frame.getWorldUpdater()).willReturn(worldUpdater);
  }

  private void givenLedgers() {
    given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
  }

}
