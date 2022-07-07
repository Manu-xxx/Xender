package com.hedera.services.yahcli.config.domain;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import java.util.Map;

public class GlobalConfig {
	private String defaultNetwork;
	private Map<String, NetConfig> networks;

	public Map<String, NetConfig> getNetworks() {
		return networks;
	}

	public void setNetworks(Map<String, NetConfig> networks) {
		this.networks = networks;
	}

	public String getDefaultNetwork() {
		return defaultNetwork;
	}

	public void setDefaultNetwork(String defaultNetwork) {
		this.defaultNetwork = defaultNetwork;
	}
}
