/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AddressBookNetworkUtils}
 */
class AddressBookNetworkUtilsTests {

    @Test
    @DisplayName("Determine If Local Node")
    void determineLocalNodeAddress() throws UnknownHostException, SocketException {
        final AddressBook addressBook =
                new RandomAddressBookGenerator().setSize(2).build();
        final Address address = addressBook.getAddress(addressBook.getNodeId(0));

        final Address loopBackAddress = address.copySetAddressInternalIpv4(
                Inet4Address.getLoopbackAddress().getAddress());
        assertTrue(AddressBookNetworkUtils.isLocal(loopBackAddress));

        final Address localIpAddress = address.copySetAddressInternalIpv4(
                Inet4Address.getByName(Network.getInternalIPAddress()).getAddress());
        assertTrue(AddressBookNetworkUtils.isLocal(localIpAddress));

        final InetAddress inetAddress = Inet4Address.getByName(Network.getInternalIPAddress());
        assertTrue(Network.isOwn(inetAddress));

        final Address notLocalAddress =
                address.copySetAddressInternalIpv4(Inet4Address.getByAddress(new byte[] {(byte) 192, (byte) 168, 0, 1})
                        .getAddress());
        assertFalse(AddressBookNetworkUtils.isLocal(notLocalAddress));

        final Address badLocalAddress = address.copySetAddressInternalIpv4(new byte[] {8, 8, 8});
        assertThrows(IllegalStateException.class, () -> AddressBookNetworkUtils.isLocal(badLocalAddress));
    }
}
