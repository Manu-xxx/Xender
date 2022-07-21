package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_IS_TOKEN_FROZEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenFreezeUnFreezeWrapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class IsFrozenPrecompileTest {
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
	@Mock private FeeObject mockFeeObject;
	@Mock private ImpliedTransfersMarshal impliedTransfers;
	@Mock private FeeCalculator feeCalculator;
	@Mock private StateView stateView;
	@Mock private ContractAliases aliases;
	@Mock private HederaStackedWorldStateUpdater worldUpdater;
	@Mock private WorldLedgers wrappedLedgers;
	@Mock private UsagePricesProvider resourceCosts;
	@Mock private HbarCentExchange exchange;
	@Mock private TransactionBody.Builder mockSynthBodyBuilder;
	@Mock private InfrastructureFactory infrastructureFactory;
	@Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
	@Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
	@Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;

	@Mock
	private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
			tokenRels;

	@Mock private AssetsLoader assetLoader;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() throws IOException {
		Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
		canonicalPrices.put(
				HederaFunctionality.TokenUnfreezeAccount,
				Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
		given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
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
	void computeCallsCorrectImplementationForIsFrozenFungibleToken() {
		// given
		final var output =
				"0x000000000000000000000000000000000000000000000000000000000000"
						+ "00160000000000000000000000000000000000000000000000000000000000000001";
		final var successOutput =
				Bytes.fromHexString(
						"0x000000000000000000000000000000000000000000000000000000000000001600000000000"
								+ "00000000000000000000000000000000000000000000000000001");
		final Bytes pretendArguments =
				Bytes.concatenate(
						Bytes.of(Integers.toBytes(ABI_IS_TOKEN_FROZEN)),
						fungibleTokenAddr,
						accountAddr);
		givenMinimalFrameContext();
		givenMinimalFeesContext();
		givenLedgers();
		givenMinimalContextForSuccessfulCall();
		Bytes input = Bytes.of(Integers.toBytes(ABI_IS_TOKEN_FROZEN));
		given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
				.willReturn(mockSynthBodyBuilder);
		given(decoder.decodeIsFrozen(any(), any())).willReturn(tokenFreezeUnFreezeWrapper);
		given(encoder.encodeIsFrozen(true)).willReturn(successResult);
		given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
		given(tokenRels.get(any(), any())).willReturn(Boolean.TRUE);
		given(encoder.encodeIsFrozen(true)).willReturn(Bytes.fromHexString(output));
		given(frame.getValue()).willReturn(Wei.ZERO);

		// when
		subject.prepareFields(frame);
		subject.prepareComputation(input, a -> a);
		final var result = subject.computeInternal(frame);

		// then
		assertEquals(successOutput, result);
	}

	private void givenMinimalFrameContext() {
		given(frame.getSenderAddress()).willReturn(contractAddress);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
	}

	private void givenMinimalContextForSuccessfulCall() {
		Optional<WorldUpdater> parent = Optional.of(worldUpdater);
		given(worldUpdater.parentUpdater()).willReturn(parent);
		given(worldUpdater.aliases()).willReturn(aliases);
		given(aliases.resolveForEvm(any()))
				.willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
		given(worldUpdater.permissivelyUnaliased(any()))
				.willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
	}

	private void givenLedgers() {
		given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
		given(wrappedLedgers.accounts()).willReturn(accounts);
		given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
		given(wrappedLedgers.nfts()).willReturn(nfts);
		given(wrappedLedgers.tokens()).willReturn(tokens);
	}

	private void givenMinimalFeesContext() {
		given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
				.willReturn(mockFeeObject);
		given(
				feeCalculator.estimatedGasPriceInTinybars(
						HederaFunctionality.ContractCall, timestamp))
				.willReturn(1L);
	}
}
