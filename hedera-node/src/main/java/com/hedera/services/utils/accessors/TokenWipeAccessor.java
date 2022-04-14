package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;

import java.util.List;

import static com.hedera.services.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;

public class TokenWipeAccessor extends SignedTxnAccessor {
	private final TokenWipeAccountTransactionBody body;
	private final AliasManager aliasManager;
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;


	public TokenWipeAccessor(final byte[] txn,
			final AliasManager aliasManager,
			final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator) throws InvalidProtocolBufferException {
		super(txn);
		this.body = getTxn().getTokenWipe();
		this.aliasManager = aliasManager;
		this.dynamicProperties = dynamicProperties;
		this.validator = validator;
	}


	public Id accountToWipe() {
		return EntityIdUtils.unaliased(body.getAccount(), aliasManager).toId();
	}

	public Id targetToken() {
		return Id.fromGrpcToken(body.getToken());
	}

	public List<Long> serialNums() {
		return body.getSerialNumbersList();
	}

	public long amount() {
		return body.getAmount();
	}

	@Override
	public boolean supportsPrecheck() {
		return true;
	}

	@Override
	public ResponseCodeEnum doPrecheck() {
		if (!body.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		if (!body.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}
		return validateTokenOpsWith(
				body.getSerialNumbersCount(),
				body.getAmount(),
				dynamicProperties.areNftsEnabled(),
				INVALID_WIPING_AMOUNT,
				body.getSerialNumbersList(),
				validator::maxBatchSizeWipeCheck
		);
	}
}

