package com.hedera.services.context;

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

import com.swirlds.common.PlatformStatus;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.swirlds.common.PlatformStatus.STARTING_UP;

@Singleton
public final class ContextPlatformStatus implements CurrentPlatformStatus {
	private PlatformStatus current = STARTING_UP;

	@Inject
	public ContextPlatformStatus() {
		/* No-op */
	}

	@Override
	public synchronized void set(final PlatformStatus status) {
		current = status;
	}

	@Override
	public synchronized PlatformStatus get() {
		return current;
	}
}
