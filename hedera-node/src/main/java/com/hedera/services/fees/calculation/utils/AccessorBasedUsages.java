package com.hedera.services.fees.calculation.utils;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class AccessorBasedUsages {
	private static final EnumSet<HederaFunctionality> supportedOps = EnumSet.of(
			CryptoTransfer,
			ConsensusSubmitMessage);

	private final CryptoOpsUsage cryptoOpsUsage;
	private final GlobalDynamicProperties dynamicProperties;

	public AccessorBasedUsages(
			CryptoOpsUsage cryptoOpsUsage,
			GlobalDynamicProperties dynamicProperties
	) {
		this.cryptoOpsUsage = cryptoOpsUsage;
		this.dynamicProperties = dynamicProperties;
	}

	public void assess(SigUsage sigUsage, TxnAccessor accessor, UsageAccumulator into) {
		final var function = accessor.getFunction();
		if (!supportedOps.contains(function)) {
			throw new IllegalArgumentException("Usage estimation for " + function + " not yet migrated");
		}

		final var baseMeta = accessor.baseUsageMeta();
		if (function == CryptoTransfer) {
			final var xferMeta = accessor.availXferUsageMeta();
			xferMeta.setTokenMultiplier(dynamicProperties.feesTokenTransferUsageMultiplier());
			cryptoOpsUsage.cryptoTransferUsage(sigUsage, xferMeta, baseMeta, into);
		} else if (function == ConsensusSubmitMessage) {
			throw new AssertionError("Not implemented!");
		}
	}
}
