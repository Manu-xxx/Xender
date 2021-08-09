package com.hedera.test.factories.scenarios;

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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.state.submerkle.FcCustomFee.royaltyFee;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenCreateFactory.newSignedTokenCreate;

public enum TokenCreateScenarios implements TxnHandlingScenario {
	TOKEN_CREATE_WITH_ADMIN_ONLY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.nonPayerKts(TOKEN_ADMIN_KT)
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_ADMIN_AND_FREEZE {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate().frozen()
							.nonPayerKts(TOKEN_ADMIN_KT, TOKEN_FREEZE_KT)
							.get()
			));
		}
	},
	TOKEN_CREATE_MISSING_ADMIN {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate().missingAdmin().get()
			));
		}
	},
	TOKEN_CREATE_WITH_AUTO_RENEW {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.autoRenew(MISC_ACCOUNT)
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_MISSING_AUTO_RENEW {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.autoRenew(MISSING_ACCOUNT)
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(fixedFee(123L, null, collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(fixedFee(123L, MISSING_ENTITY_ID, collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(fixedFee(123L, null, collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(fractionalFee(
									1, 2,
									3, 4,
									collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(royaltyFee(
									1, 2,
									null, collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_MISSING_COLLECTOR {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var collector = EntityId.fromGrpcAccountId(MISSING_ACCOUNT);
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.plusCustomFee(fixedFee(123L, null, collector))
							.get()
			));
		}
	},
	TOKEN_CREATE_WITH_MISSING_TREASURY {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenCreate()
							.missingAdmin()
							.treasury(MISSING_ACCOUNT)
							.get()
			));
		}
	},
}
