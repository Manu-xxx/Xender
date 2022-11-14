package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.annotation.Nullable;

/**
 * Record representing the result of a key lookup for signature requirements. If the key lookup
 * succeeds, failureReason will be null. Else if, key lookup failed failureReason will be set
 * providing information about the failure and the key will be set to null.
 * In some cases, when the key need not be looked up when receiver signature required is false,
 * both key and failureReason are null.
 */
public record KeyOrLookupFailureReason(@Nullable HederaKey key, @Nullable ResponseCodeEnum failureReason) {
	public static final KeyOrLookupFailureReason PRESENT_BUT_NOT_REQUIRED = new KeyOrLookupFailureReason(null, null);

	public boolean failed() {
		return failureReason != null;
	}
	public static KeyOrLookupFailureReason withFailureReason(final ResponseCodeEnum response){
		return new KeyOrLookupFailureReason(null, response);
	}

	public static KeyOrLookupFailureReason withKey(final HederaKey key){
		return new KeyOrLookupFailureReason(key, null);
	}

	public void incorporateTo(final SigTransactionMetadata meta) {
		// If we've already failed in computing earlier sig reqs, do nothing
		if (meta.failed()) {
			return;
		}
		if (failureReason != null) {
			meta.setStatus(failureReason);
		} else if (key != null) {
			meta.addToReqKeys(key);
		}
		// If neither condition above is met, we are PRESENT_BUT_NOT_REQUIRED
	}
}
