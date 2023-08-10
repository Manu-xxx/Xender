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

import static com.swirlds.cli.utility.HtmlTagFactory.CLASS_NAME_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.ELAPSED_TIME_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.FILTERS_DIV_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.FILTER_COLUMN_DIV_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.HIDER_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.HIDER_LABEL_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_BODY_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_BREAK_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_CHECKBOX_TYPE;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_CLASS_ATTRIBUTE;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_DIV_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_H3_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_HEAD_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_HTML_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_INPUT_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_LABEL_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_SCRIPT_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_SOURCE_ATTRIBUTE;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_STYLE_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_TABLE_TAG;
import static com.swirlds.cli.utility.HtmlTagFactory.HTML_TYPE_ATTRIBUTE;
import static com.swirlds.cli.utility.HtmlTagFactory.LOG_BODY_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.LOG_LEVEL_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.LOG_NUMBER_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.MARKER_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.NODE_ID_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.REMAINDER_OF_LINE_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.SECTION_HEADING;
import static com.swirlds.cli.utility.HtmlTagFactory.THREAD_NAME_COLUMN_LABEL;
import static com.swirlds.cli.utility.HtmlTagFactory.TIMESTAMP_COLUMN_LABEL;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility methods for processing log files into a more readable format.
 */
public class LogProcessingUtils {
    /**
     * Hidden constructor.
     */
    private LogProcessingUtils() {}

    /**
     * Parse a log timestamp string into an Instant.
     *
     * @param timestampString the timestamp string to parse
     * @param zoneId          the zone ID of the timestamp
     * @return the parsed Instant
     */
    public static Instant parseTimestamp(@NonNull final String timestampString, @NonNull final ZoneId zoneId) {
        return LocalDateTime.parse(timestampString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                .atZone(zoneId)
                .toInstant();
    }

    /**
     * Generate an ANSI colorized version of a log line if the line can be parsed.
     * <p>
     * If the line cannot be parsed, it is returned without any colorization.
     *
     * @param inputString the input log line string
     * @param zoneId      the timezone of the timestamp in the log line
     * @return the colorized log line if it can be parsed, otherwise the original log line
     */
    @NonNull
    static String colorizeLogLineAnsi(@NonNull final String inputString, @NonNull final ZoneId zoneId) {
        try {
            final LogLine logLine = new LogLine(inputString, zoneId);

            return logLine.generateAnsiString();
        } catch (final Exception e) {
            return inputString;
        }
    }

    private static String createHiderCheckbox(@NonNull final String elementName) {
        final String inputTag = new HtmlTagFactory(HTML_INPUT_TAG, null, true)
                .addAttribute(HTML_CLASS_ATTRIBUTE, List.of(HIDER_LABEL, elementName))
                .addAttribute(HTML_TYPE_ATTRIBUTE, HTML_CHECKBOX_TYPE)
                .generateTag();

        final String labelTag = new HtmlTagFactory(HTML_LABEL_TAG, "Hide " + elementName, false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, List.of(HIDER_LABEL_LABEL))
                .generateTag();

        final String breakTag = new HtmlTagFactory(HTML_BREAK_TAG, null, true).generateTag();

        return inputTag + "\n" + labelTag + "\n" + breakTag + "\n";
    }

    public static String generateHtmlPage(@NonNull final List<String> logLineStrings) {
        final List<LogLine> logLines = logLineStrings.stream()
                .map(string -> {
                    try {
                        return new LogLine(string, ZoneId.systemDefault());
                    } catch (final Exception e) {
                        // TODO handle this case
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        final List<String> formattedLogLines =
                logLines.stream().map(LogLine::generateHtmlString).toList();

        final List<String> css = new ArrayList<>();
        // TODO use the variables instead of literal class names
        // TODO better yet, just make a css factory
        css.add("[data-hide]:not([data-hide~='0']):not([data-hide~=\"NaN\"]) {display: none;}");
        css.add(".filters {display: flex;}");
        css.add(".filter-column {padding-left: 2em;}");

        final String hiderCssTag = new HtmlTagFactory(HTML_STYLE_TAG, String.join("\n", css), false).generateTag();

        final String minJsSource = "https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js";
        final String minJsSourceTag = new HtmlTagFactory(HTML_SCRIPT_TAG, "", false)
                .addAttribute(HTML_SOURCE_ATTRIBUTE, minJsSource)
                .generateTag();

        final String headTag = new HtmlTagFactory(
                        HTML_HEAD_TAG, "\n" + hiderCssTag + "\n" + minJsSourceTag + "\n", false)
                .generateTag();

        final String logTableTag = new HtmlTagFactory(
                        HTML_TABLE_TAG, "\n" + String.join("\n", formattedLogLines) + "\n", false)
                .generateTag();

        // TODO read this from file instead??
        final String TEMP_hiderJs =
                """
                        // the checkboxes that have the ability to hide things
                        var hiders = document.getElementsByClassName("hider");

                        // add a listener to each checkbox
                        for (var i = 0; i < hiders.length; i++) {
                            hiders[i].addEventListener("click", function() {
                                // the classes that exist on the checkbox that is clicked
                                var checkboxClasses = this.classList;

                                // the name of the class that should be hidden
                                // each checkbox has 2 classes, "hider", and the name of the class to be hidden
                                var toggleClass;
                                for (j = 0; j < checkboxClasses.length; j++) {
                                    if (checkboxClasses[j] == "hider") {
                                        continue;
                                    }

                                    toggleClass = checkboxClasses[j];
                                    break;
                                }

                                // these are the objects on the page which match the class to toggle (discluding the input boxes)
                                var matchingObjects = $("." + toggleClass).not("input");

                                // go through each of the matching objects, and modify the hide count according to the value of the checkbox
                                for (j = 0; j < matchingObjects.length; j++) {
                                    var currentHideCount = parseInt($(matchingObjects[j]).attr('data-hide')) || 0;

                                    var newHideCount;
                                    if ($(this).is(":checked")) {
                                        newHideCount = currentHideCount + 1;
                                    } else {
                                        newHideCount = currentHideCount - 1;
                                    }

                                    $(matchingObjects[j]).attr('data-hide', newHideCount);
                                }
                            });
                        }
                        """;

        final String scriptTag = new HtmlTagFactory(HTML_SCRIPT_TAG, TEMP_hiderJs, false).generateTag();

        final String filterColumnsHeading = new HtmlTagFactory(HTML_H3_TAG, "Filter Columns", false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, SECTION_HEADING)
                .generateTag();
        final List<String> columnFilterCheckboxes = Stream.of(
                        NODE_ID_COLUMN_LABEL,
                        ELAPSED_TIME_COLUMN_LABEL,
                        TIMESTAMP_COLUMN_LABEL,
                        LOG_NUMBER_COLUMN_LABEL,
                        LOG_LEVEL_COLUMN_LABEL,
                        MARKER_COLUMN_LABEL,
                        THREAD_NAME_COLUMN_LABEL,
                        CLASS_NAME_COLUMN_LABEL,
                        REMAINDER_OF_LINE_COLUMN_LABEL)
                .map(LogProcessingUtils::createHiderCheckbox)
                .toList();

        final String filterLogLevelHeading = new HtmlTagFactory(HTML_H3_TAG, "Filter Log Level", false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, SECTION_HEADING)
                .generateTag();
        final List<String> existingLogLevels =
                logLines.stream().map(LogLine::getLogLevel).distinct().toList();
        final List<String> logLevelFilterCheckboxes = existingLogLevels.stream()
                .map(LogProcessingUtils::createHiderCheckbox)
                .toList();

        final String filterMarkerHeading = new HtmlTagFactory(HTML_H3_TAG, "Filter Marker", false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, SECTION_HEADING)
                .generateTag();
        final List<String> existingMarkers =
                logLines.stream().map(LogLine::getMarker).distinct().toList();
        final List<String> logMarkerFilterCheckboxes = existingMarkers.stream()
                .map(LogProcessingUtils::createHiderCheckbox)
                .toList();

        final String filterColumnsDiv = new HtmlTagFactory(
                        HTML_DIV_TAG,
                        "\n" + filterColumnsHeading + "\n" + String.join("\n", columnFilterCheckboxes),
                        false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, FILTER_COLUMN_DIV_LABEL)
                .generateTag();

        final String filterLogLevelDiv = new HtmlTagFactory(
                        HTML_DIV_TAG,
                        "\n" + filterLogLevelHeading + "\n" + String.join("\n", logLevelFilterCheckboxes),
                        false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, FILTER_COLUMN_DIV_LABEL)
                .generateTag();

        final String filterMarkerDiv = new HtmlTagFactory(
                        HTML_DIV_TAG,
                        "\n" + filterMarkerHeading + "\n" + String.join("\n", logMarkerFilterCheckboxes),
                        false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, FILTER_COLUMN_DIV_LABEL)
                .generateTag();

        final String filtersDiv = new HtmlTagFactory(
                        HTML_DIV_TAG,
                        "\n" + filterColumnsDiv + "\n" + filterLogLevelDiv + "\n" + filterMarkerDiv + "\n",
                        false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, FILTERS_DIV_LABEL)
                .generateTag();

        final String logsHeading = new HtmlTagFactory(HTML_H3_TAG, "Logs", false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, SECTION_HEADING)
                .generateTag();

        final List<String> bodyElements = new ArrayList<>();
        bodyElements.add(filtersDiv);
        bodyElements.add(logsHeading);
        bodyElements.add(logTableTag);
        bodyElements.add(scriptTag);

        final String bodyTag = new HtmlTagFactory(HTML_BODY_TAG, String.join("\n", bodyElements), false)
                .addAttribute(HTML_CLASS_ATTRIBUTE, LOG_BODY_LABEL)
                .generateTag();

        final List<String> pageElements = List.of(headTag, bodyTag);

        return new HtmlTagFactory(HTML_HTML_TAG, "\n" + String.join("\n", pageElements) + "\n", false).generateTag();
    }
}
