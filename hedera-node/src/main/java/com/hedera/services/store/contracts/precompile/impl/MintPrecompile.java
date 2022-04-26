package com.hedera.services.store.contracts.precompile.impl;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.MINT_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class MintPrecompile implements Precompile {
	private static final List<ByteString> NO_METADATA = Collections.emptyList();

	private final WorldLedgers ledgers;
	private final DecodingFacade decoder;
	private final EncodingFacade encoder;
	private final ContractAliases aliases;
	private final EvmSigsVerifier sigsVerifier;
	private final RecordsHistorian recordsHistorian;
	private final SideEffectsTracker sideEffects;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final InfrastructureFactory infrastructureFactory;
	private final PrecompilePricingUtils pricingUtils;

	private MintWrapper mintOp;

	public MintPrecompile(
			final WorldLedgers ledgers,
			final DecodingFacade decoder,
			final EncodingFacade encoder,
			final ContractAliases aliases,
			final EvmSigsVerifier sigsVerifier,
			final RecordsHistorian recordsHistorian,
			final SideEffectsTracker sideEffects,
			final SyntheticTxnFactory syntheticTxnFactory,
			final InfrastructureFactory infrastructureFactory,
			final PrecompilePricingUtils pricingUtils
	) {
		this.ledgers = ledgers;
		this.decoder = decoder;
		this.encoder = encoder;
		this.aliases = aliases;
		this.sigsVerifier = sigsVerifier;
		this.sideEffects = sideEffects;
		this.recordsHistorian = recordsHistorian;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.infrastructureFactory = infrastructureFactory;
		this.pricingUtils = pricingUtils;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		mintOp = decoder.decodeMint(input);
		return syntheticTxnFactory.createMint(mintOp);
	}

	@Override
	public void run(final MessageFrame frame) {
		// --- Check required signatures ---
		final var tokenId = Id.fromGrpcToken(Objects.requireNonNull(mintOp).tokenType());
		final var hasRequiredSigs = KeyActivationUtils.validateKey(
				frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey, ledgers, aliases);
		validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

		/* --- Build the necessary infrastructure to execute the transaction --- */
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
		final var mintLogic = infrastructureFactory.newMintLogic(accountStore, tokenStore);

		/* --- Execute the transaction and capture its results --- */
		if (mintOp.type() == NON_FUNGIBLE_UNIQUE) {
			final var newMeta = mintOp.metadata();
			final var creationTime = recordsHistorian.nextFollowingChildConsensusTime();
			mintLogic.mint(tokenId, newMeta.size(), 0, newMeta, creationTime);
		} else {
			mintLogic.mint(tokenId, 0, mintOp.amount(), NO_METADATA, Instant.EPOCH);
		}
	}

	@Override
	public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
		final var isNftMint = Objects.requireNonNull(mintOp).type() == NON_FUNGIBLE_UNIQUE;
		return pricingUtils.getMinimumPriceInTinybars(isNftMint ? MINT_NFT : MINT_FUNGIBLE, consensusTime);
	}

	@Override
	public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
		final var receiptBuilder = childRecord.getReceiptBuilder();
		validateTrue(receiptBuilder != null, FAIL_INVALID);
		return encoder.encodeMintSuccess(
				childRecord.getReceiptBuilder().getNewTotalSupply(),
				childRecord.getReceiptBuilder().getSerialNumbers());
	}

	@Override
	public Bytes getFailureResultFor(final ResponseCodeEnum status) {
		return encoder.encodeMintFailure(status);
	}
}
