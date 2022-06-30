package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class MintPrecompile extends AbstractWritePrecompile {
	private static final List<ByteString> NO_METADATA = Collections.emptyList();
	private static final String MINT = String.format(FAILURE_MESSAGE, "mint");
	private final EncodingFacade encoder;
	private final ContractAliases aliases;
	private final EvmSigsVerifier sigsVerifier;
	private final RecordsHistorian recordsHistorian;

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
		super(ledgers, decoder, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
		this.encoder = encoder;
		this.aliases = aliases;
		this.sigsVerifier = sigsVerifier;
		this.recordsHistorian = recordsHistorian;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		this.transactionBody = null;
		mintOp = decoder.decodeMint(input);
		transactionBody = syntheticTxnFactory.createMint(mintOp);
		return transactionBody;
	}

	@Override
	public void run(final MessageFrame frame) {
		// --- Check required signatures ---
		final var tokenId = Id.fromGrpcToken(Objects.requireNonNull(mintOp).tokenType());
		final var hasRequiredSigs = KeyActivationUtils.validateKey(
				frame, tokenId.asEvmAddress(), sigsVerifier::hasActiveSupplyKey, ledgers, aliases);
		validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, MINT);

		/* --- Build the necessary infrastructure to execute the transaction --- */
		final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
		final var tokenStore = infrastructureFactory.newTokenStore(
				accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
		final var mintLogic = infrastructureFactory.newMintLogic(accountStore, tokenStore);

		final var validity = mintLogic.validateSyntax(transactionBody.build());
		validateTrue(validity == OK, validity);

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
