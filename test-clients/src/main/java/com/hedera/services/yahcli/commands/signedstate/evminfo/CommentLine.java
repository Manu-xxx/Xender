/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import edu.umd.cs.findbugs.annotations.NonNull;

/** Represents a full-line comment in the generated assembly */
public record CommentLine(@NonNull String comment) implements Assembly.Line {

    @Override
    public void formatLine(StringBuilder sb) {
        extendWithBlanksTo(sb, Columns.COMMENT.getColumn());
        sb.append(Assembly.FULL_LINE_COMMENT_PREFIX);
        sb.append(comment);
    }
}
