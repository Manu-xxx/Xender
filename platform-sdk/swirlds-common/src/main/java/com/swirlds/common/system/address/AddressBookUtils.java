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
 * The last line of the config.txt address book contains the nextNodeId value in the form of: `nextNodeId, 23`
 */
public class AddressBookUtils {

    public static final String ADDRESS_KEYWORD = "address";
    public static final String NEXT_NODE_ID_KEYWORD = "nextNodeId";

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
            final boolean hasInternalIpv4 = address.getHostnameInternal() != null;
            final boolean hasExternalIpv4 = address.getHostnameExternal() != null;
            table.addRow(
                    "address,",
                    address.getNodeId() + ",",
                    address.getNickname() + ",",
                    address.getSelfName() + ",",
                    address.getWeight() + ",",
                    (hasInternalIpv4 ? address.getHostnameInternal() : "") + ",",
                    address.getPortInternal() + ",",
                    (hasExternalIpv4 ? address.getHostnameExternal() : "") + ",",
                    address.getPortExternal() + (hasMemo ? "," : ""),
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
                final NodeId nodeId = parseNextNodeId(trimmedLine);
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
            throw new ParseException("The address book text does not contain a `nextNodeId` line.", 0);
        }
        return addressBook;
    }

    /**
     * Parse the next available node id from a single line of text.  The line must start with the keyword `nextNodeId`
     * followed by a comma and then the node id.  The node id must be a positive integer greater than all nodeIds in the
     * address book.
     *
     * @param nextNodeId the text to parse.
     * @return the parsed node id.
     * @throws ParseException if there is any problem with parsing the node id.
     */
    @NonNull
    public static NodeId parseNextNodeId(@NonNull final String nextNodeId) throws ParseException {
        Objects.requireNonNull(nextNodeId, "The nextNodeId must not be null.");
        final String[] parts = nextNodeId.split(",");
        if (parts.length != 2) {
            throw new ParseException(
                    "The nextNodeId [%s] does not have exactly 2 comma separated parts.".formatted(nextNodeId), 0);
        }
        if (!parts[0].trim().equals(NEXT_NODE_ID_KEYWORD)) {
            throw new ParseException(
                    "The nextNodeId [%s] does not start with the keyword `nextNodeId`.".formatted(nextNodeId), 0);
        }
        final String nodeIdText = parts[1].trim();
        try {
            final long nodeId = Long.parseLong(nodeIdText);
            if (nodeId < 0) {
                throw new ParseException(
                        "The nextNodeId [%s] does not have a positive integer node id.".formatted(nextNodeId), 1);
            }
            return new NodeId(nodeId);
        } catch (final NumberFormatException e) {
            throw new ParseException(
                    "The nextNodeId [%s] does not have a positive integer node id.".formatted(nextNodeId), 1);
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
                internalIp.getHostAddress(),
                internalPort,
                externalIp.getHostAddress(),
                externalPort,
                memoToUse);
    }

    /**
     * Verifies that all addresses and the nextNodeId are the same between the two address books, otherwise an
     * IllegalStateException is thrown.  All other fields in the address book are intentionally ignored. This comparison
     * is used during reconnect to verify that the address books align enough to proceed.
     *
     * @param addressBook1 the first address book to compare.
     * @param addressBook2 the second address book to compare.
     * @throws IllegalStateException if the address books are not compatible for reconnect.
     */
    public static void verifyReconnectAddressBooks(
            @NonNull final AddressBook addressBook1, @NonNull final AddressBook addressBook2)
            throws IllegalStateException {
        if (!addressBook1.getNextNodeId().equals(addressBook2.getNextNodeId())) {
            throw new IllegalStateException("The next node ids are not the same.");
        }
        final int addressCount = addressBook1.getSize();
        if (addressCount != addressBook2.getSize()) {
            throw new IllegalStateException("The address books do not have the same number of addresses.");
        }
        for (int i = 0; i < addressCount; i++) {
            final NodeId nodeId1 = addressBook1.getNodeId(i);
            final NodeId nodeId2 = addressBook2.getNodeId(i);
            if (!nodeId1.equals(nodeId2)) {
                throw new IllegalStateException("The address books do not have the same node ids.");
            }
            final Address address1 = addressBook1.getAddress(nodeId1);
            final Address address2 = addressBook2.getAddress(nodeId2);
            if (!address1.equals(address2)) {
                throw new IllegalStateException("The address books do not have the same addresses.");
            }
        }
    }
}
