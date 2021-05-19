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

import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.CryptoUpdateFactory.newSignedCryptoUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum CryptoUpdateScenarios implements TxnHandlingScenario {
	CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(MISC_ACCOUNT_ID).get()
			));
		}
	},
	CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(MISSING_ACCOUNT_ID).get()
			));
		}
	},
	CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(COMPLEX_KEY_ACCOUNT_ID)
							.nonPayerKts(COMPLEX_KEY_ACCOUNT_KT)
							.get()
			));
		}
	},
	CRYPTO_UPDATE_COMPLEX_KEY_ACCOUNT_ADD_NEW_KEY_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(COMPLEX_KEY_ACCOUNT_ID)
							.newAccountKt(NEW_ACCOUNT_KT)
							.nonPayerKts(COMPLEX_KEY_ACCOUNT_KT, NEW_ACCOUNT_KT)
							.get()
			));
		}
	},
	CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(MISC_ACCOUNT_ID)
							.newAccountKt(NEW_ACCOUNT_KT)
							.get()
			));
		}
	},
	CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(SYS_ACCOUNT_ID)
							.newAccountKt(SYS_ACCOUNT_KT)
							.get()
			));
		}
	},
	CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedCryptoUpdate(SYS_ACCOUNT_ID).get()
			));
		}
	}
}
