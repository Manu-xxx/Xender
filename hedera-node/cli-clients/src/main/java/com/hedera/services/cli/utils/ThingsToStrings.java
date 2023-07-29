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

package com.hedera.services.cli.utils;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.swirlds.common.crypto.CryptographyHolder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThingsToStrings {

    private static final Pattern quotesNeeded = Pattern.compile("[\"\n;]");

    @NonNull
    public static String quoteForCsv(@Nullable String s) {
        if (s == null) s = "";
        s = s.replace("\"", "\"\""); // quote double-quotes
        if (quotesNeeded.matcher(s).find()) s = '"' + s + '"';
        return s;
    }

    // All of these converters from something to a String will check to see if that something is a "null" or is
    // an "empty" (whatever "empty" might mean for that thing).  They return `true` iff the thing _existed, otherwise
    // (null or "empty") return false.  (Thus, they are predicates.)

    // P.S. I had started by naming nearly all of them simply `toString`.  (The couple of exceptions were the ones
    // taking a `Set` or `Map` which couldn't be named that way because of erasure: They'd collide.) But I soon had
    // to give them different names as it turns out that method references and overloading don't mix, with Java.

    private static final HexFormat hexer = HexFormat.of().withUpperCase();

    public static boolean toStringOfByteString(@NonNull final StringBuilder sb, @Nullable final ByteString bs) {
        if (bs == null || bs.size() == 0) return false;

        final var a = bs.toByteArray();
        hexer.formatHex(sb, a);
        return true;
    }

    public static boolean toStringOfByteArray(@NonNull final StringBuilder sb, @Nullable final byte[] bs) {
        if (bs == null || bs.length == 0) return false;

        hexer.formatHex(sb, bs);
        return true;
    }

    public static boolean toStringOfEntityNumPair(
            @NonNull final StringBuilder sb, @Nullable final EntityNumPair entityNumPair) {
        if (entityNumPair == null || entityNumPair.equals(EntityNumPair.MISSING_NUM_PAIR)) return false;

        sb.append("(");
        sb.append(entityNumPair.getHiOrderAsLong());
        sb.append(",");
        sb.append(entityNumPair.getLowOrderAsLong());
        sb.append(")");
        return true;
    }

    public static boolean toStringOfEntityNum(@NonNull final StringBuilder sb, @Nullable final EntityNum entityNum) {
        if (entityNum == null || entityNum.equals(EntityNum.MISSING_NUM)) return false;

        sb.append(entityNum.longValue());
        return true;
    }

    public static boolean toStringOfEntityId(@NonNull final StringBuilder sb, @Nullable final EntityId entityId) {
        if (entityId == null || entityId.equals(EntityId.MISSING_ENTITY_ID)) return false;

        sb.append(entityId.toAbbrevString());
        return true;
    }

    public static boolean toStringOfIntArray(@NonNull final StringBuilder sb, @Nullable final int[] ints) {
        if (ints == null || ints.length == 0) return false;

        sb.append(Arrays.stream(ints).mapToObj(Integer::toString).collect(Collectors.joining(",", "(", ")")));
        return true;
    }

    /** Writes a cryptographic hash of the actual key */
    @SuppressWarnings(
            "java:S5738") // 'deprecated' code marked for removal - it's practically impossible to use the platform sdk
    // these days w/o running into deprecated methods
    public static boolean toStringOfJKey(@NonNull final StringBuilder sb, @Nullable final JKey jkey) {
        if (jkey == null || jkey.isEmpty()) return false;

        try {
            final var ser = jkey.serialize();
            final var hash = CryptographyHolder.get().digestSync(ser).getValue();
            toStringOfByteArray(sb, hash);
        } catch (final IOException ex) {
            sb.append("**EXCEPTION**");
        }
        return true;
    }

    public static boolean toStringOfContractKey(@NonNull final StringBuilder sb, @Nullable final ContractKey ckey) {
        if (ckey == null) return false;

        sb.append("(");
        sb.append(ckey.getContractId());
        sb.append(",");
        sb.append(ckey.getKeyAsBigInteger());
        sb.append(")");
        return true;
    }

    public static boolean toStringOfFcTokenAllowanceId(
            @NonNull final StringBuilder sb, @Nullable final FcTokenAllowanceId id) {
        if (id == null) return false;

        var r = true;
        sb.append("(");
        r &= toStringOfEntityNum(sb, id.getTokenNum());
        sb.append(",");
        r &= toStringOfEntityNum(sb, id.getSpenderNum());
        sb.append(")");
        return r;
    }

    public static boolean toStringOfFcTokenAllowanceIdSet(
            @NonNull final StringBuilder sb, @Nullable final Set<FcTokenAllowanceId> ids) {
        if (ids == null || ids.isEmpty()) return false;

        final var orderedIds = ids.stream().sorted().toList();
        sb.append("(");
        for (final var id : orderedIds) {
            toStringOfFcTokenAllowanceId(sb, id);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    public static boolean toStringOfMapEnLong(
            @NonNull final StringBuilder sb, @Nullable final Map<EntityNum, Long> map) {
        if (map == null || map.isEmpty()) return false;

        final var orderedEntries = new TreeMap<>(map);
        sb.append("(");
        for (final var kv : orderedEntries.entrySet()) {
            toStringOfEntityNum(sb, kv.getKey());
            sb.append("->");
            sb.append(kv.getValue());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    public static boolean toStringOfMapFcLong(
            @NonNull final StringBuilder sb, @Nullable final Map<FcTokenAllowanceId, Long> map) {
        if (map == null || map.isEmpty()) return false;

        final var orderedEntries = new TreeMap<>(map);
        sb.append("(");
        for (final var kv : orderedEntries.entrySet()) {
            toStringOfFcTokenAllowanceId(sb, kv.getKey());
            sb.append("->");
            sb.append(kv.getValue());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    private ThingsToStrings() {}
}
