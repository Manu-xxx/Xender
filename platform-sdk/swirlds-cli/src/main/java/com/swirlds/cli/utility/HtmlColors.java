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

package com.swirlds.cli.utility;

import com.swirlds.common.formatting.TextEffect;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;

/**
 * Allows for mapping ANSI colors to HTML colors
 */
public class HtmlColors {
    /**
     * Hidden constructor
     */
    private HtmlColors() {}

    /**
     * The map of ANSI colors to HTML colors. The html colors are experimentally determined with a color picker
     */
    public static final Map<TextEffect, String> ansiToHtmlColors = Map.of(
            TextEffect.WHITE, "#808181",
            TextEffect.GRAY, "#595858",
            TextEffect.BRIGHT_RED, "#f13f4c",
            TextEffect.BRIGHT_GREEN, "#4db815",
            TextEffect.BRIGHT_YELLOW, "#e5be01",
            TextEffect.BRIGHT_BLUE, "#1ea6ee",
            TextEffect.BRIGHT_PURPLE, "#ed7fec",
            TextEffect.BRIGHT_CYAN, "#00e5e5",
            TextEffect.BRIGHT_WHITE, "#fdfcfc");

    /**
     * Get the HTML color for the given ANSI color
     *
     * @param ansiColor the ANSI color
     * @return the HTML color, or null if the map doesn't contain the given ANSI color
     */
    @NonNull
    public static String getHtmlColor(@NonNull final TextEffect ansiColor) {
        Objects.requireNonNull(ansiColor);

        if (!ansiToHtmlColors.containsKey(ansiColor)) {
            return "";
        }

        return ansiToHtmlColors.get(ansiColor);
    }
}
