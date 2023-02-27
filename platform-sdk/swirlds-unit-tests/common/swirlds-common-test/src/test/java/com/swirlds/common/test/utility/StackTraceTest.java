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

package com.swirlds.common.test.utility;

import static com.swirlds.common.utility.StackTrace.getStackTrace;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.utility.StackTrace;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link StackTrace} record.
 */
@DisplayName("StackTrace Test")
class StackTraceTest {

    /** Regular expression used to validate the first line of the stack trace generated below. */
    private static final Pattern FIRST_LINE_MATCHER = Pattern.compile(
            "(com|org|net)\\..*StackTraceTest\\.stackTraceTest\\(StackTraceTest.java:[0-9]+\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Verifies the first line of the stack trace generated by the {@link StackTrace#getStackTrace()} utility method
     * properly ignores the first 2 stack frames.
     */
    @Test
    @DisplayName("Stack Trace Test")
    void stackTraceTest() {
        final String stackTrace = getStackTrace().toString();

        final String firstLine = stackTrace.split("\n")[0];
        assertTrue(
                FIRST_LINE_MATCHER.matcher(firstLine).matches(),
                "The first line of stack trace doesn't match expected. This may fail if the name of this class or test method changes.");
    }
}
