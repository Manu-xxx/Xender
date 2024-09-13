/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hederaaccountnumalias;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

public class HederaAccountNumAliasTranslator extends AbstractCallTranslator<HasCallAttempt> {

    public static final Function HEDERA_ACCOUNT_NUM_ALIAS =
            new Function("getHederaAccountNumAlias(address)", ReturnTypes.RESPONSE_CODE_ADDRESS);

    @Inject
    public HederaAccountNumAliasTranslator() {
        // Dagger
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull HederaAccountNumAliasCall callFrom(@NonNull HasCallAttempt attempt) {
        final Address address = HEDERA_ACCOUNT_NUM_ALIAS
                .decodeCall(attempt.input().toArrayUnsafe())
                .get(0);
        return new HederaAccountNumAliasCall(attempt, address);
    }

    @Override
    public boolean matches(@NonNull HasCallAttempt attempt) {
        requireNonNull(attempt, "attempt");
        return attempt.isSelector(HEDERA_ACCOUNT_NUM_ALIAS);
    }
}
