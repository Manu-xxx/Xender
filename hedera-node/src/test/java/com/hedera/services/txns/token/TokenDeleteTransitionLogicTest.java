package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class TokenDeleteTransitionLogicTest {
	private TokenID tokenId = IdUtils.asToken("0.0.12345");
	private AccountID account = IdUtils.asAccount("0.0.54321");
	private Token modelToken;
	private Account modelAccountTreasury;

	private TokenStore tokenStore;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;

	private TransactionBody tokenDeleteTxn;
	private TokenDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		tokenStore = mock(TokenStore.class);
		accessor = mock(PlatformTxnAccessor.class);

		txnCtx = mock(TransactionContext.class);

		modelToken = mock(Token.class);
		modelAccountTreasury = mock(Account.class);

		subject = new TokenDeleteTransitionLogic(tokenStore, txnCtx);
	}

	@Test
	void capturesInvalidDelete() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.delete(tokenId)).willReturn(INVALID_TOKEN_ID);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_TOKEN_ID);
	}

	@Test
	void capturesInvalidDeletionDueToAlreadyDeleted() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.delete(tokenId)).willReturn(TOKEN_WAS_DELETED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
	}

	@Test
	void followsHappyPath() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.delete(tokenId)).willReturn(OK);
		given(modelToken.getTreasury()).willReturn(modelAccountTreasury);
		given(modelAccountTreasury.getId()).willReturn(Id.fromGrpcAccount(account));
		// when:
		subject.doStateTransition();

		// then:
		verify(tokenStore).delete(tokenId);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(tokenDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void setsFailInvalidIfUnhandledException() {
		givenValidTxnCtx();
		// and:
		given(tokenStore.delete(any())).willThrow(IllegalArgumentException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	void acceptsValidTxn() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(tokenDeleteTxn));
	}

	@Test
	void rejectsMissingToken() {
		givenMissingToken();

		// expect:
		assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenDeleteTxn));
	}

	private void givenValidTxnCtx() {
		tokenDeleteTxn = TransactionBody.newBuilder()
				.setTokenDeletion(TokenDeleteTransactionBody.newBuilder()
						.setToken(tokenId))
				.build();
		given(accessor.getTxn()).willReturn(tokenDeleteTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void givenMissingToken() {
		tokenDeleteTxn = TransactionBody.newBuilder()
				.setTokenDeletion(TokenDeleteTransactionBody.newBuilder())
				.build();
	}
}
