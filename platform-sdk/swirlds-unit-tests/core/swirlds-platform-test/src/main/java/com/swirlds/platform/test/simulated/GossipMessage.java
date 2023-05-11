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

package com.swirlds.platform.test.simulated;

import com.swirlds.common.io.SelfSerializable;

/**
 * A test message that is used for simulated gossip.
 *
 * @param message     the message to gossip
 * @param senderId    the sender of the message
 * @param recipientId the node to send the message to, or {@code null} if broadcast to all
 */
public record GossipMessage(SelfSerializable message, long senderId, Long recipientId) {

    /**
     * Create a gossip message with all nodes as recipients.
     *
     * @param message  the message to gossip
     * @param senderId the of the sender
     * @return the gossip message
     */
    public static GossipMessage toAll(final SelfSerializable message, final long senderId) {
        return new GossipMessage(message, senderId, null);
    }

    /**
     * Create a gossip message with a single node recipient.
     *
     * @param message     the message to gossip
     * @param senderId    the of the sender
     * @param recipientId the recipient of the message
     * @return the gossip message
     */
    public static GossipMessage toPeer(final SelfSerializable message, final long senderId, final long recipientId) {
        return new GossipMessage(message, senderId, recipientId);
    }

    @Override
    public String toString() {
        return message.toString() + " from " + senderId;
    }
}
