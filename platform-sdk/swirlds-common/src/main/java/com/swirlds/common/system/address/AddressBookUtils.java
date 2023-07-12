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

package com.swirlds.common.system.address;

import static com.swirlds.common.system.address.Address.ipString;

import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Objects;

/**
 * A utility class for AddressBook functionality.
 * <p>
 * Each line in the config.txt address book contains the following comma separated elements:
 * <ul>
 *     <li>the keyword "address"</li>
 *     <li>node id</li>
 *     <li>nickname</li>
 *     <li>self name</li>
 *     <li>weight</li>
 *     <li>internal IP address</li>
 *     <li>internal port</li>
 *     <li>external IP address</li>
 *     <li>external port</li>
 *     <li>memo field (optional)</li>
 * </ul>
 * Example: `address, 22, node22, node22, 1, 10.10.11.12, 5060, 212.25.36.123, 5060, memo for node 22`
 * <p>
 * The last line of the config.txt address book contains the nextNodeId value in the form of: `nextnodeid, 23`
 */
public class AddressBookUtils {

    public static final String ADDRESS_KEYWORD = "address";
    public static final String NEXT_NODE_ID_KEYWORD = "nextnodeid";

    private AddressBookUtils() {}

    /**
     * Serializes an AddressBook to text in the form used by config.txt.
     *
     * @param addressBook the address book to serialize.
     * @return the config.txt compatible text representation of the address book.
     */
    @NonNull
    public static String addressBookConfigText(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "The addressBook must not be null.");
        final TextTable table = new TextTable().setBordersEnabled(false);
        for (final Address address : addressBook) {
            final String memo = address.getMemo();
            final boolean hasMemo = !memo.trim().isEmpty();
            final boolean hasInternalIpv4 = address.getAddressInternalIpv4() != null;
            final boolean hasExternalIpv4 = address.getAddressExternalIpv4() != null;
            table.addRow(
                    "address,",
                    address.getNodeId() + ",",
                    address.getNickname() + ",",
                    address.getSelfName() + ",",
                    address.getWeight() + ",",
                    (hasInternalIpv4 ? ipString(address.getAddressInternalIpv4()) : "") + ",",
                    address.getPortInternalIpv4() + ",",
                    (hasExternalIpv4 ? ipString(address.getAddressExternalIpv4()) : "") + ",",
                    address.getPortExternalIpv4() + (hasMemo ? "," : ""),
                    memo);
        }
        final String addresses = table.render();
        return addresses + "\n" + NEXT_NODE_ID_KEYWORD + ", " + addressBook.getNextNodeId();
    }

    /**
     * Parses an address book from text in the form described by config.txt.  Comments are ignored.
     *
     * @param addressBookText the config.txt compatible serialized address book to parse.
     * @return a parsed AddressBook.
     * @throws ParseException if any Address throws a ParseException when being parsed.
     */
    @NonNull
    public static AddressBook parseAddressBookText(@NonNull final String addressBookText) throws ParseException {
        Objects.requireNonNull(addressBookText, "The addressBookText must not be null.");
        final AddressBook addressBook = new AddressBook();
        boolean nextNodeIdParsed = false;
        for (final String line : addressBookText.split("\\r?\\n")) {
            final String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }
            if (trimmedLine.startsWith(ADDRESS_KEYWORD)) {
                final Address address = parseAddressText(trimmedLine);
                if (address != null) {
                    addressBook.add(address);
                }
            } else if (trimmedLine.startsWith(NEXT_NODE_ID_KEYWORD)) {
                final NodeId nodeId = parseNextAvailableNodeId(trimmedLine);
                addressBook.setNextNodeId(nodeId);
                nextNodeIdParsed = true;
            } else {
                throw new ParseException(
                        "The line [%s] does not start with `%s` or `%s`."
                                .formatted(line.substring(0, 30), ADDRESS_KEYWORD, NEXT_NODE_ID_KEYWORD),
                        0);
            }
        }
        if (!nextNodeIdParsed) {
            throw new ParseException("The address book text does not contain a `nextAvailableNodeId` line.", 0);
        }
        return addressBook;
    }

    /**
     * Parse the next available node id from a single line of text.  The line must start with the keyword
     * `nextAvailableNodeId` followed by a comma and then the node id.  The node id must be a positive integer greater
     * than all nodeIds in the address book.
     *
     * @param nextAvailableNodeIdText the text to parse.
     * @return the parsed node id.
     * @throws ParseException if there is any problem with parsing the node id.
     */
    @NonNull
    public static NodeId parseNextAvailableNodeId(@NonNull final String nextAvailableNodeIdText) throws ParseException {
        Objects.requireNonNull(nextAvailableNodeIdText, "The nextAvailableNodeIdText must not be null.");
        final String[] parts = nextAvailableNodeIdText.split(",");
        if (parts.length != 2) {
            throw new ParseException(
                    "The nextAvailableNodeIdText [%s] does not have exactly 2 comma separated parts."
                            .formatted(nextAvailableNodeIdText),
                    0);
        }
        if (!parts[0].trim().equals(NEXT_NODE_ID_KEYWORD)) {
            throw new ParseException(
                    "The nextAvailableNodeIdText [%s] does not start with the keyword `nextAvailableNodeId`."
                            .formatted(nextAvailableNodeIdText),
                    0);
        }
        final String nodeIdText = parts[1].trim();
        try {
            final long nodeId = Long.parseLong(nodeIdText);
            if (nodeId < 0) {
                throw new ParseException(
                        "The nextAvailableNodeIdText [%s] does not have a positive integer node id."
                                .formatted(nextAvailableNodeIdText),
                        1);
            }
            return new NodeId(nodeId);
        } catch (final NumberFormatException e) {
            throw new ParseException(
                    "The nextAvailableNodeIdText [%s] does not have a positive integer node id."
                            .formatted(nextAvailableNodeIdText),
                    1);
        }
    }

    /**
     * Parse an address from a single line of text, if it exists.  Address lines may have comments which start with the
     * `#` character.  Comments are ignored.  Lines which are just comments return null.  If there is content prior to a
     * `#` character, parsing the address is attempted.  Any failure to generate an address will result in throwing a
     * parse exception.  The address parts are comma separated.   The format of text addresses prevent the use of `#`
     * and `,` characters in any of the text based fields, including the memo field.
     *
     * @param addressText the text to parse.
     * @return the parsed address or null if the line is a comment.
     * @throws ParseException if there is any problem with parsing the address.
     */
    @Nullable
    public static Address parseAddressText(@NonNull final String addressText) throws ParseException {
        Objects.requireNonNull(addressText, "The addressText must not be null.");
        // lines may have comments which start with the first # character.
        final String[] textAndComment = addressText.split("#");
        if (textAndComment.length == 0
                || textAndComment[0] == null
                || textAndComment[0].trim().isEmpty()) {
            return null;
        }
        final String[] parts = addressText.split(",");
        if (parts.length < 9 || parts.length > 10) {
            throw new ParseException("Incorrect number of parts in the address line to parse correctly.", parts.length);
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        if (!parts[0].equals(ADDRESS_KEYWORD)) {
            throw new ParseException("The address line must start with 'address' and not '" + parts[0] + "'", 0);
        }
        final NodeId nodeId;
        try {
            nodeId = new NodeId(Long.parseLong(parts[1]));
        } catch (final Exception e) {
            throw new ParseException("Cannot parse node id from '" + parts[1] + "'", 1);
        }
        final String nickname = parts[2];
        final String selfname = parts[3];
        final long weight;
        try {
            weight = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse value of weight from '" + parts[4] + "'", 4);
        }
        final InetAddress internalIp;
        try {
            internalIp = InetAddress.getByName(parts[5]);
        } catch (UnknownHostException e) {
            throw new ParseException("Cannot parse ip address from '" + parts[5] + ",", 5);
        }
        final int internalPort;
        try {
            internalPort = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[6] + "'", 6);
        }
        final InetAddress externalIp;
        try {
            externalIp = InetAddress.getByName(parts[7]);
        } catch (UnknownHostException e) {
            throw new ParseException("Cannot parse ip address from '" + parts[7] + ",", 7);
        }
        final int externalPort;
        try {
            externalPort = Integer.parseInt(parts[8]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[8] + "'", 8);
        }
        final String memoToUse = parts.length == 10 ? parts[9] : "";

        return new Address(
                nodeId,
                nickname,
                selfname,
                weight,
                internalIp.getAddress(),
                internalPort,
                externalIp.getAddress(),
                externalPort,
                memoToUse);
    }

    /**
     * Determine a new next node id value from an address book and the old next node id value.
     *
     * @param addressBook   the address book to use
     * @param oldNextNodeId the old next node id value
     * @return the new next node id value
     */
    @NonNull
    public static NodeId determineNewNextNodeId(
            @NonNull final AddressBook addressBook, @Nullable final NodeId oldNextNodeId) {
        final int addressBookSize = addressBook.getSize();
        if (addressBookSize <= 0) {
            return Objects.requireNonNullElse(oldNextNodeId, NodeId.FIRST_NODE_ID);
        }
        final NodeId lastNodeId = addressBook.getNodeId(addressBookSize - 1);
        if (oldNextNodeId != null && lastNodeId.compareTo(oldNextNodeId) < 0) {
            // the last node id in the address book is not higher than the old NextNodeId
            return oldNextNodeId;
        } else {
            // the last node id in the address book is high enough to bump the next node id value.
            return lastNodeId.getOffset(1);
        }
    }
}
