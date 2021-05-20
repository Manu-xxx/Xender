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

import static com.hedera.test.factories.txns.FileAppendFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.FileAppendFactory.TREASURY_PAYER_ID;
import static com.hedera.test.factories.txns.FileAppendFactory.newSignedFileAppend;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

public enum FileAppendScenarios implements TxnHandlingScenario {
	VANILLA_FILE_APPEND_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedFileAppend(MISC_FILE_ID).get()
			));
		}
	},
	TREASURY_SYS_FILE_APPEND_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedFileAppend(SYS_FILE_ID)
							.payer(TREASURY_PAYER_ID)
							.get()
			));
		}
	},
	MASTER_SYS_FILE_APPEND_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedFileAppend(SYS_FILE_ID)
							.payer(MASTER_PAYER_ID)
							.get()
			));
		}
	},
	IMMUTABLE_FILE_APPEND_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedFileAppend(IMMUTABLE_FILE_ID).get()
			));
		}
	},
	FILE_APPEND_MISSING_TARGET_SCENARIO {
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedFileAppend(MISSING_FILE_ID).get()
			));
		}
	}
}
