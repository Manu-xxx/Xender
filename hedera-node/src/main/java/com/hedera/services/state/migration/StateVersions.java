package com.hedera.services.state.migration;

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

/**
 * Gives the versions of the current and previous world states.
 */
public final class StateVersions {
	// For the record,
	//   - Release 0.7.x was state version 1
	//   - Release 0.8.x was state version 2
	//   - Release 0.9.x was state version 3
	//   - Release 0.10.x was state version 4
	//   - Release 0.11.x was state version 5
	//   - Release 0.12.x was state version 6
	//   - Release 0.13.x was state version 7
	//   - Release 0.14.x was state version 8
	//   - Release 0.15.x was state version 9
	//   - Release 0.16.x was state version 10
	//   - Release 0.17.x was state version 11
	//   - Release 0.18.x was state version 12
	//   - Release 0.19.x and 0.20.x were state version 13
	//   - Release 0.21.x was state version 14
	//   - Release 0.22.x was state version 15
	public static final int RELEASE_024X_VERSION = 17;
	public static final int RELEASE_025X_VERSION = 18;
	public static final int RELEASE_0260_VERSION = 20;

	public static final int MINIMUM_SUPPORTED_VERSION = RELEASE_024X_VERSION;
	public static final int CURRENT_VERSION = RELEASE_0260_VERSION;

	private StateVersions() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
