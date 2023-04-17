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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.node.app.service.contract.impl.test.handlers.SigReqAdapterUtils.wellKnownAccountsState;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.Map;
import org.mockito.Mockito;

// NOTE: This class is duplicated in more than one service module.
// !!!!!!!!!!🔥🔥🔥 It should be deleted once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
public class AdapterUtils {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountAccess} containing the "well-known" accounts and aliases that
     * exist in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in
     * unit tests that require an {@link AccountAccess}.
     *
     * @return the well-known account store
     */
    public static AccountAccess wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(
                ALIASES_KEY, wellKnownAliasState(),
                ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = Mockito.mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static MapReadableKVState<String, EntityNumValue> wellKnownAliasState() {
        final Map<String, EntityNumValue> wellKnownAliases = Map.ofEntries(
                Map.entry(CURRENTLY_UNUSED_ALIAS, new EntityNumValue(MISSING_NUM.longValue())),
                Map.entry(
                        NO_RECEIVER_SIG_ALIAS,
                        new EntityNumValue(fromAccountId(NO_RECEIVER_SIG).longValue())),
                Map.entry(
                        RECEIVER_SIG_ALIAS,
                        new EntityNumValue(fromAccountId(RECEIVER_SIG).longValue())),
                Map.entry(
                        FIRST_TOKEN_SENDER_LITERAL_ALIAS.toStringUtf8(),
                        new EntityNumValue(fromAccountId(FIRST_TOKEN_SENDER).longValue())));
        return new MapReadableKVState<>(ALIASES_KEY, wellKnownAliases);
    }
}
