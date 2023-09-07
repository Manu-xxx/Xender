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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VirtualTerminal Tests")
class VirtualTerminalTests {

    @Test
    @DisplayName("Successful Command With Output Test")
    void successfulCommandWithOutputTest() {
        final VirtualTerminal terminal = new VirtualTerminal();

        final CommandResult result = terminal.run("echo", "Hello World!");

        assertTrue(result.isSuccessful());
        assertEquals(0, result.exitCode());
        assertEquals("Hello World!\n", result.out());
        assertEquals("", result.error());
    }

    @Test
    @DisplayName("Successful Command Without Output Test")
    void successfulCommandWithoutOutputTest() {
        final VirtualTerminal terminal = new VirtualTerminal();

        assertFalse(Files.exists(Path.of("asdf")));

        final CommandResult result1 = terminal.run("touch", "asdf");

        assertTrue(result1.isSuccessful());
        assertEquals(0, result1.exitCode());
        assertEquals("", result1.out());
        assertEquals("", result1.error());

        assertTrue(Files.exists(Path.of("asdf")));

        final CommandResult result2 = terminal.run("rm", "asdf");

        assertTrue(result2.isSuccessful());
        assertEquals(0, result2.exitCode());
        assertEquals("", result2.out());
        assertEquals("", result2.error());

        assertFalse(Files.exists(Path.of("asdf")));
    }

    @Test
    @DisplayName("Failing Command Test")
    void failingCommandTest() {
        final VirtualTerminal terminal = new VirtualTerminal();
        terminal.setPrintStdout(true).setPrintStderr(true).setPrintCommand(true).setPrintExitCode(true);

        final CommandResult result = terminal.run("rm", "foo/bar/baz");

        assertFalse(result.isSuccessful());
        assertEquals("rm: foo/bar/baz: No such file or directory\n", result.error());
        assertEquals(1, result.exitCode());
    }
}
