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

import static com.swirlds.cli.utility.HtmlGenerator.HTML_SPAN_TAG;

import com.swirlds.common.formatting.TextEffect;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.regex.Pattern;

/**
 * Class representing the portion of a log line that is specific to platform status
 * <p>
 * Example:
 * Platform spent 66.0 ms in REPLAYING_EVENTS.
 * Now in OBSERVING {"oldStatus":"REPLAYING_EVENTS","newStatus":"OBSERVING"}
 * [com.swirlds.logging.payloads.PlatformStatusPayload]
 */
public class PlatformStatusLog implements FormattableString {
    /**
     * Regex for parsing platform status log line remainder
     */
    public static final String PLATFORM_STATUS_LOG_LINE_REGEX = "(.*spent )(.*)( in )(.*)(\\. Now in )([A-Z_]+)(.*)";

    public static final TextEffect STATUS_COLOR = TextEffect.BRIGHT_PURPLE;

    public static final String STATUS_HTML_CLASS = "status-detail";

    /**
     * The full original string
     */
    private final String originalString;

    /**
     * "Platform spent"
     */
    private final String platformSpent;
    /**
     * How long the platform spent in the previous status
     */
    private final String duration;
    /**
     * "in"
     */
    private final String in;
    /**
     * The previous platform status
     */
    private final String previousStatus;
    /**
     * "Now in"
     */
    private final String nowIn;
    /**
     * The new platform status
     */
    private final String newStatus;
    /**
     * The rest of the line after the new platform status
     */
    private final String statusMessageRemainder;

    /**
     * Constructor
     *
     * @param inputString the input string, which is platform status specific portion of the log
     */
    public PlatformStatusLog(@NonNull final String inputString) {
        final var statusMatch = Pattern.compile(PLATFORM_STATUS_LOG_LINE_REGEX).matcher(inputString);
        if (!statusMatch.matches()) {
            throw new IllegalArgumentException("String does not match expected format: " + inputString);
        }

        originalString = inputString;

        platformSpent = statusMatch.group(1);
        duration = statusMatch.group(2);
        in = statusMatch.group(3);
        previousStatus = statusMatch.group(4);
        nowIn = statusMatch.group(5);
        newStatus = statusMatch.group(6);
        statusMessageRemainder = statusMatch.group(7);
    }

    /**
     * Get the duration of the previous platform status
     *
     * @return the duration of the previous platform status
     */
    @NonNull
    public String getDuration() {
        return duration;
    }

    /**
     * Get the previous platform status
     *
     * @return the previous platform status
     */
    @NonNull
    public String getPreviousStatus() {
        return previousStatus;
    }

    /**
     * Get the new platform status
     *
     * @return the new platform status
     */
    @NonNull
    public String getNewStatus() {
        return newStatus;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getOriginalPlaintext() {
        return originalString;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateAnsiString() {
        return platformSpent
                + STATUS_COLOR.apply(duration)
                + in
                + STATUS_COLOR.apply(previousStatus)
                + nowIn
                + STATUS_COLOR.apply(newStatus)
                + statusMessageRemainder;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateHtmlString() {
        return platformSpent
                + new HtmlTagFactory(HTML_SPAN_TAG, duration, false)
                        .addClass(STATUS_HTML_CLASS)
                        .generateTag()
                + in
                + new HtmlTagFactory(HTML_SPAN_TAG, previousStatus, false)
                        .addClass(STATUS_HTML_CLASS)
                        .generateTag()
                + nowIn
                + new HtmlTagFactory(HTML_SPAN_TAG, newStatus, false)
                        .addClass(STATUS_HTML_CLASS)
                        .generateTag();
        // intentionally skip writing the remainder of the log. it is duplicate data
    }
}
