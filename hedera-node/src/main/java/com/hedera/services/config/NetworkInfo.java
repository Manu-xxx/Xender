package com.hedera.services.config;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HexFormat;

@Singleton
public class NetworkInfo {

	private final PropertySource properties;
	private ByteString ledgerId;

	@Inject
	public NetworkInfo(@CompositeProps PropertySource properties) {
		this.properties = properties;
	}

	public ByteString ledgerId() {
		if (ledgerId == null) {
			/*
			 * Permanent ledger ids are to be set in a future specification. The provisional ids are,
			 *   0x00 -> mainnet
			 *   0x01 -> testnet
			 *   0x02 -> previewnet
			 *   0x03 -> other dev or preprod networks
			 */
			ledgerId = rationalize(properties.getStringProperty("ledger.id"));
		}
		return ledgerId;
	}

	private ByteString rationalize(String ledgerProperty) {
		if (!ledgerProperty.startsWith("0x")) {
			throw new IllegalStateException("Ledger Id set in invalid format");
		} else {
			try {
				final var hex = ledgerProperty.substring(2);
				final var bytes = HexFormat.of().parseHex(hex);
				return ByteString.copyFrom(bytes);
			} catch (Exception e) {
				throw new IllegalStateException("Ledger Id set in invalid format", e);
			}
		}
	}
}
