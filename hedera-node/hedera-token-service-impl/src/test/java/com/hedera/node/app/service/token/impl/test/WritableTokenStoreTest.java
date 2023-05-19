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

package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.test.handlers.TokenHandlerTestBase;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTokenStoreTest extends TokenHandlerTestBase {
    private Token token;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableTokenStore(null));
        assertThrows(NullPointerException.class, () -> writableTokenStore.put(null));
        assertThrows(NullPointerException.class, () -> writableTokenStore.put(null));
    }

    @Test
    void constructorCreatesTokenState() {
        final var store = new WritableTokenStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void getReturnsImmutableToken() {
        token = createToken();
        writableTokenStore.put(token);

        final var maybeReadToken = writableTokenStore.get(tokenEntityNum.longValue());

        assertTrue(maybeReadToken.isPresent());
        final var readToken = maybeReadToken.get();
        assertEquals(token, readToken);
    }

    @Test
    void getForModifyReturnsImmutableToken() {
        token = createToken();

        writableTokenStore.put(token);

        final var maybeReadToken = writableTokenStore.getForModify(tokenEntityNum.longValue());

        assertTrue(maybeReadToken.isPresent());
        final var readToken = maybeReadToken.get();
        assertEquals(token, readToken);
    }

    @Test
    void putsTokenChangesToStateInModifications() {
        token = createToken();
        assertFalse(writableTokenState.contains(tokenEntityNum));

        // put, keeps the token in the modifications
        writableTokenStore.put(token);

        assertTrue(writableTokenState.contains(tokenEntityNum));
        final var writtenToken = writableTokenState.get(tokenEntityNum);
        assertEquals(token, writtenToken);
    }

    @Test
    void getsSizeOfState() {
        token = createToken();
        assertEquals(0, writableTokenStore.sizeOfState());
        assertEquals(Collections.EMPTY_SET, writableTokenStore.modifiedTokens());
        writableTokenStore.put(token);

        assertEquals(1, writableTokenStore.sizeOfState());
        assertEquals(Set.of(tokenEntityNum), writableTokenStore.modifiedTokens());
    }
}
