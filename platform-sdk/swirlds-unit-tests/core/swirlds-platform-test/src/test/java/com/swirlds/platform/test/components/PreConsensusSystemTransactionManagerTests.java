/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.components;

class PreConsensusSystemTransactionManagerTests {

    // TODO
    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.PLATFORM)
    //    @DisplayName("tests that exceptions are handled gracefully")
    //    void testHandleExceptions() {
    //        PreConsensusSystemTransactionHandler<DummySystemTransaction> consumer = (dummySystemTransaction, aLong) ->
    // {
    //            throw new IllegalStateException("this is intentionally thrown");
    //        };
    //
    //        final PreConsensusSystemTransactionManager handler = new PreConsensusSystemTransactionManagerFactory()
    //                .addHandlers(List.of(
    //                        new PreConsensusSystemTransactionTypedHandler<>(DummySystemTransaction.class, consumer)))
    //                .build();
    //
    //        assertDoesNotThrow(() -> handler.handleEvent(newDummyEvent(1)));
    //    }
    //
    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.PLATFORM)
    //    @DisplayName("tests handling system transactions")
    //    void testHandle() {
    //        final AtomicInteger handleCount = new AtomicInteger(0);
    //
    //        PreConsensusSystemTransactionHandler<DummySystemTransaction> consumer =
    //                (dummySystemTransaction, aLong) -> handleCount.getAndIncrement();
    //
    //        final PreConsensusSystemTransactionManager handler = new PreConsensusSystemTransactionManagerFactory()
    //                .addHandlers(List.of(
    //                        new PreConsensusSystemTransactionTypedHandler<>(DummySystemTransaction.class, consumer)))
    //                .build();
    //
    //        handler.handleEvent(newDummyEvent(0));
    //        handler.handleEvent(newDummyEvent(1));
    //        handler.handleEvent(newDummyEvent(2));
    //
    //        assertEquals(3, handleCount.get(), "incorrect number of handle calls");
    //    }
    //
    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.PLATFORM)
    //    @DisplayName("tests handling system transactions, where no handle method has been defined")
    //    void testNoHandleMethod() {
    //        final PreConsensusSystemTransactionManager handler = new
    // PreConsensusSystemTransactionManagerFactory().build();
    //
    //        assertDoesNotThrow(() -> handler.handleEvent(newDummyEvent(1)), "should not throw");
    //    }
}
