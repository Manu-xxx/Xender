/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.network.communication;

import com.swirlds.common.io.utility.IOConsumer;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.Negotiator;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;

/**
 * A class for conveniently testing protocol negotiation
 */
public class NegotiatorTestSuite {
    public static final int PROTOCOL_1 = 0;
    public static final int PROTOCOL_2 = 1;
    public static final int PROTOCOL_3 = 2;
    public static final int INVALID_PROTOCOL = 3;

    private static final int SLEEP_MS = 0;

    private final TestProtocol protocol1 = new TestProtocol();
    private final TestProtocol protocol2 = new TestProtocol();
    private final TestProtocol protocol3 = new TestProtocol();
    private final List<TestProtocol> protocols = List.of(protocol1, protocol2, protocol3);
    private final OutputCheck outputCheck = new OutputCheck();
    private final TestInput input = new TestInput();
    private final ReadWriteFakeConnection connection;
    private final Negotiator negotiator;

    public NegotiatorTestSuite() {
        this(null);
    }

    public NegotiatorTestSuite(final IOConsumer<Integer> outputInterceptor) {
        connection = new ReadWriteFakeConnection(input, outputCheck, outputInterceptor);
        this.negotiator = new Negotiator(
                new NegotiationProtocols(List.of(protocol1, protocol2, protocol3)), connection, SLEEP_MS);
    }

    /**
     * @param bytes
     * 		the input to provide to the negotiator
     */
    public void input(final int... bytes) {
        input.setInput(bytes);
    }

    /**
     * @param bytes
     * 		the expected output coming out of the negotiator
     */
    public void expectedOutput(final int... bytes) {
        outputCheck.setExpected(bytes);
    }

    /**
     * Assert that a protocol ran the specified number of times
     *
     * @param protocol
     * 		the protocol ID
     * @param expectedRuns
     * 		the number of runs expected
     */
    public void assertProtocolRan(final int protocol, final int expectedRuns) {
        Assertions.assertEquals(
                expectedRuns,
                getProtocol(protocol).getTimesRan(),
                String.format("the protocol with the Id %d was expected to run %d times", protocol, expectedRuns));
    }

    /**
     * Assert that each of the protocols ran the specified number of times
     *
     * @param protocol1runs
     * 		expected runs for protocol 1
     * @param protocol2runs
     * 		expected runs for protocol 2
     * @param protocol3runs
     * 		expected runs for protocol 3
     */
    public void assertProtocolRuns(final int protocol1runs, final int protocol2runs, final int protocol3runs) {
        assertProtocolRan(PROTOCOL_1, protocol1runs);
        assertProtocolRan(PROTOCOL_2, protocol2runs);
        assertProtocolRan(PROTOCOL_3, protocol3runs);

        for (final TestProtocol protocol : protocols) {
            protocol.assertInitiateContract();
        }
    }

    /**
     * @param protocol
     * 		the protocol ID
     * @return the protocol with the supplied ID
     */
    public TestProtocol getProtocol(final int protocol) {
        return protocols.get(protocol);
    }

    /**
     * Execute a single negotiation
     */
    public void execute() throws InterruptedException, NegotiationException, NetworkProtocolException, IOException {
        negotiator.execute();
    }

    /**
     * Reset everything to the initial state, except the negotiator
     */
    public void reset() {
        for (final TestProtocol protocol : protocols) {
            protocol.reset();
        }
        input.reset();
        outputCheck.reset();
    }
}
