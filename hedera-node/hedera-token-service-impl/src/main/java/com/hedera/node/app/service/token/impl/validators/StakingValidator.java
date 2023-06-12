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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.STAKING_NOT_ENABLED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validations needed for staking related fields in token operations
 */
@Singleton
public class StakingValidator {
    private NodeInfo nodeInfo;
    private ConfigProvider configProvider;

    @Inject
    public StakingValidator(NodeInfo nodeInfo, ConfigProvider configProvider) {
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
    }

    /**
     * Validates staked id if present
     * @param hasDeclineRewardChange if the transaction body has decline reward field to be updated
     * @param stakedIdKind staked id kind (account or node)
     * @param stakedAccountIdInOp staked account id
     * @param stakedNodeIdInOp staked node id
     * @param accountStore readable account store
     */
    public void validateStakedId(
            @NonNull final boolean hasDeclineRewardChange,
            @NonNull final String stakedIdKind,
            @Nullable final AccountID stakedAccountIdInOp,
            @Nullable final Long stakedNodeIdInOp,
            @NonNull ReadableAccountStore accountStore) {
        final var hasStakingId = stakedAccountIdInOp != null || stakedNodeIdInOp != null;
        final var stakingConfig = configProvider.getConfiguration().getConfigData(StakingConfig.class);
        // If staking is not enabled, then can't update staked id
        validateFalse(!stakingConfig.isEnabled() && (hasStakingId || hasDeclineRewardChange), STAKING_NOT_ENABLED);

        // sentinel values on -1 for stakedNodeId and 0.0.0 for stakedAccountId are used to reset
        // staking on an account
        if (isValidStakingSentinel(stakedIdKind, stakedAccountIdInOp, stakedNodeIdInOp)) {
            return;
        }
        // If the stakedId is not sentinel values, then validate the accountId is present in account store
        // or nodeId is valid
        if (stakedIdKind.equals("STAKED_ACCOUNT_ID")) {
            validateTrue(accountStore.getAccountById(requireNonNull(stakedAccountIdInOp)) != null, INVALID_STAKING_ID);
        } else if (stakedIdKind.equals("STAKED_NODE_ID")) {
            validateTrue(nodeInfo.isValidId((requireNonNull(stakedNodeIdInOp).longValue())), INVALID_STAKING_ID);
        }
    }

    /**
     * Validates if the staked id is a sentinel value. Sentinel values are used to reset staking
     * on an account. The sentinel values are -1 for stakedNodeId and 0.0.0 for stakedAccountId.
     * @param stakedIdKind staked id kind
     * @param stakedAccountId staked account id
     * @param stakedNodeId staked node id
     * @return true if staked id is a sentinel value
     */
    private boolean isValidStakingSentinel(
            @NonNull String stakedIdKind, @Nullable AccountID stakedAccountId, @Nullable Long stakedNodeId) {
        if (stakedIdKind.equals("STAKED_ACCOUNT_ID")) {
            // current checking only account num since shard and realm are 0.0
            return requireNonNull(stakedAccountId).accountNum() == 0;
        } else if (stakedIdKind.equals("STAKED_NODE_ID")) {
            return requireNonNull(stakedNodeId).longValue() == -1;
        } else {
            return false;
        }
    }
}
