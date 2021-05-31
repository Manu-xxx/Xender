package com.hedera.services.sigs;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigStatusOrderResultFactory;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.legacy.crypto.SignatureStatusCode.SUCCESS;
import static com.hedera.services.sigs.PlatformSigOps.createEd25519PlatformSigsFrom;
import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.utils.StatusUtils.successFor;
import static com.swirlds.common.crypto.VerificationStatus.UNKNOWN;

public class Rationalization {
	private static final Logger log = LogManager.getLogger(Rationalization.class);

	public final static SigStatusOrderResultFactory IN_HANDLE_SUMMARY_FACTORY =
			new SigStatusOrderResultFactory(true);

	private final SyncVerifier syncVerifier;
	private final List<TransactionSignature> txnSigs;
	private final TxnAccessor txnAccessor;
	private final HederaSigningOrder keyOrderer;
	private final Function<TxnAccessor, PubKeyToSigBytes> pkToSigFnProvider;
	private final TxnScopedPlatformSigFactory sigFactory;

	public Rationalization(
			TxnAccessor txnAccessor,
			SyncVerifier syncVerifier,
			HederaSigningOrder keyOrderer,
			Function<TxnAccessor, PubKeyToSigBytes> pkToSigFnProvider,
			Function<TxnAccessor, TxnScopedPlatformSigFactory> sigFactoryCreator
	) {
		this.txnAccessor = txnAccessor;
		this.syncVerifier = syncVerifier;
		this.keyOrderer = keyOrderer;
		this.pkToSigFnProvider = pkToSigFnProvider;

		txnSigs = txnAccessor.getPlatformTxn().getSignatures();
		sigFactory = sigFactoryCreator.apply(txnAccessor);
	}

	public SignatureStatus execute() {
		List<TransactionSignature> realPayerSigs = new ArrayList<>(), realOtherPartySigs = new ArrayList<>();

		final var pkToSigFn = pkToSigFnProvider.apply(txnAccessor);
		var payerStatus = expandIn(pkToSigFn, realPayerSigs, keyOrderer::keysForPayer);
		if (!SUCCESS.equals(payerStatus.getStatusCode())) {
			return payerStatus;
		}
		var otherPartiesStatus = expandIn(pkToSigFn, realOtherPartySigs, keyOrderer::keysForOtherParties);
		if (!SUCCESS.equals(otherPartiesStatus.getStatusCode())) {
			return otherPartiesStatus;
		}

		var rationalizedPayerSigs = rationalize(realPayerSigs, 0);
		var rationalizedOtherPartySigs = rationalize(realOtherPartySigs, realPayerSigs.size());

		if (rationalizedPayerSigs == realPayerSigs || rationalizedOtherPartySigs == realOtherPartySigs) {
			txnAccessor.getPlatformTxn().clear();
			txnAccessor.getPlatformTxn().addAll(rationalizedPayerSigs.toArray(new TransactionSignature[0]));
			txnAccessor.getPlatformTxn().addAll(rationalizedOtherPartySigs.toArray(new TransactionSignature[0]));
			log.warn("Verified crypto sigs synchronously for txn {}", txnAccessor.getSignedTxn4Log());
			return syncSuccess();
		}

		return asyncSuccess();
	}

	private List<TransactionSignature> rationalize(List<TransactionSignature> realSigs, int startingAt) {
		try {
			var candidateSigs = txnSigs.subList(startingAt, startingAt + realSigs.size());
			if (allVaryingMaterialEquals(candidateSigs, realSigs) && allStatusesAreKnown(candidateSigs)) {
				return candidateSigs;
			}
		} catch (IndexOutOfBoundsException ignore) { }
		syncVerifier.verifySync(realSigs);
		return realSigs;
	}

	private boolean allStatusesAreKnown(List<TransactionSignature> sigs) {
		for (final var sig : sigs) {
			if (sig.getSignatureStatus() == UNKNOWN) {
				return false;
			}
		}
		return true;
	}

	private SignatureStatus expandIn(
			PubKeyToSigBytes pkToSigFn,
			List<TransactionSignature> target,
			BiFunction<TransactionBody, SigStatusOrderResultFactory, SigningOrderResult<SignatureStatus>> keysFn
	) {
		SigningOrderResult<SignatureStatus> orderResult =
				keysFn.apply(txnAccessor.getTxn(), IN_HANDLE_SUMMARY_FACTORY);
		if (orderResult.hasErrorReport()) {
			return orderResult.getErrorReport();
		}
		PlatformSigsCreationResult creationResult = createEd25519PlatformSigsFrom(
				orderResult.getOrderedKeys(), pkToSigFn, sigFactory);
		if (creationResult.hasFailed()) {
			return creationResult.asSignatureStatus(true, txnAccessor.getTxnId());
		}
		target.addAll(creationResult.getPlatformSigs());
		return successFor(true, txnAccessor);
	}

	private SignatureStatus syncSuccess() {
		return success(SignatureStatusCode.SUCCESS_VERIFY_SYNC);
	}

	private SignatureStatus asyncSuccess() {
		return success(SignatureStatusCode.SUCCESS_VERIFY_ASYNC);
	}

	private SignatureStatus success(SignatureStatusCode code) {
		return new SignatureStatus(
				code, ResponseCodeEnum.OK,
				true, txnAccessor.getTxn().getTransactionID(),
				null, null, null, null);
	}
}
