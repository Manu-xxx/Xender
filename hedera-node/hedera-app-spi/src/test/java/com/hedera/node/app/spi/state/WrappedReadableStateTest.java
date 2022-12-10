/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state;

import java.util.Map;

/**
 * This test extends the {@link ReadableStateBaseTest}, getting all the test methods used there, but
 * this time executed on a {@link WrappedReadableState}.
 */
class WrappedReadableStateTest extends ReadableStateBaseTest {
    @Override
    protected ReadableStateBase<String, String> createFruitState(Map<String, String> backingMap) {
        final var delegate = super.createFruitState(backingMap);
        return new WrappedReadableState<>(delegate);
    }

    protected ReadableStateBase<String, String> createFruitState() {
        return new WrappedReadableState<>(readableFruitState());
    }
}
