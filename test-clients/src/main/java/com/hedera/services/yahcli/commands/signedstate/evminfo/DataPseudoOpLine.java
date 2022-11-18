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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Columns;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Options;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public record DataPseudoOpLine(int codeOffset, Kind kind, int[] operandBytes, String eolComment)
        implements Assembly.Line, Assembly.Code {

    public enum Kind {
        DATA,
        METADATA,
        DATA_OVERRUN
    }

    public DataPseudoOpLine {
        // De-null operands
        operandBytes = null != operandBytes ? operandBytes : new int[0];
        eolComment = null != eolComment ? eolComment : "";
    }

    // NOTTODO: There's a LOT of commonality here with CodeLine! like maybe this should be a
    // subclass
    // (except: can't do that because it's a record, and this doesn't seem like the kind of thing
    // that should be a default method of the interface)
    // ...
    public void formatLine(StringBuilder sb) {
        if (Options.DISPLAY_CODE_OFFSET.getValue()) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append(String.format("%5X", codeOffset));
        }
        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(kind.name());

        if (0 != operandBytes.length) {
            extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
            sb.append(Utility.toHex(operandBytes));
        }
        if (!eolComment.isEmpty()) {
            if (Columns.EOL_COMMENT.getColumn() - 1 > sb.length()) {
                extendWithBlanksTo(sb, Columns.EOL_COMMENT.getColumn());
            } else {
                sb.append("  ");
            }
            sb.append(Assembly.EOL_COMMENT_PREFIX);
            sb.append(eolComment);
        }
    }

    public int getCodeOffset() {
        return codeOffset;
    }

    public int getCodeSize() {
        return operandBytes.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof DataPseudoOpLine other) {
            return codeOffset == other.codeOffset
                    && Arrays.equals(operandBytes, other.operandBytes)
                    && eolComment.equals(other.eolComment)
                    && kind.equals(other.kind);
        } else return false;
    }

    @Override
    public int hashCode() {
        int result = codeOffset;
        result = 31 * result + Arrays.hashCode(operandBytes);
        result = 31 * result + eolComment.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }

    @Override
    public @NotNull String toString() {
        return String.format(
                "CodeLine[codeOffset=%04X, operandBytes=%s, eolComment='%s', kind=%s]",
                codeOffset, Utility.toHex(operandBytes), eolComment, kind.toString());
    }
}
