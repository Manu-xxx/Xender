/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.contract.impl.exec.v045;

import com.hedera.node.app.service.contract.impl.exec.v034.Version034FeatureFlags;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Version045FeatureFlags extends Version034FeatureFlags {
    @Inject
    public Version045FeatureFlags() {
        // Dagger2
    }

    @Override
    public boolean isAllowCallsToNonContractAccountsEnabled(
            @NonNull Configuration config, @Nullable HederaEvmAccount possiblyGrandfatheredAddress) {
        final var grandfathered = possiblyGrandfatheredAddress != null
                && ConversionUtils.isLongZero(possiblyGrandfatheredAddress.getAddress())
                && config.getConfigData(ContractsConfig.class).evmNonExtantContractsFail()
                    .contains(ConversionUtils.numberOfLongZero(possiblyGrandfatheredAddress.getAddress()));
        return config.getConfigData(ContractsConfig.class).evmAllowCallsToNonContractAccounts() && !grandfathered;
    }
}
