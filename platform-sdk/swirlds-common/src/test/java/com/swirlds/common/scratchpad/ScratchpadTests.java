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

package com.swirlds.common.scratchpad;

import static com.swirlds.common.scratchpad.TestScratchpadType.BAR;
import static com.swirlds.common.scratchpad.TestScratchpadType.BAZ;
import static com.swirlds.common.scratchpad.TestScratchpadType.FOO;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.config.StateConfig_;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.merkle.utility.SerializableLong;
import com.swirlds.common.system.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Scratchpad Tests")
class ScratchpadTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private PlatformContext platformContext;

    private final NodeId selfId = new NodeId(0);

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        Files.createDirectories(testDirectory);
        TemporaryFileBuilder.overrideTemporaryFileLocation(testDirectory.resolve("tmp"));
        final Configuration configuration = new TestConfigBuilder()
                .withValue(StateConfig_.SAVED_STATE_DIRECTORY, testDirectory.toString())
                .getOrCreateConfig();
        platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();

        final Scratchpad<TestScratchpadType> scratchpad =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        final Path scratchpadDirectory =
                testDirectory.resolve("scratchpad").resolve("0").resolve("test");

        // No scratchpad file will exist until we write the first value
        assertFalse(scratchpadDirectory.toFile().exists());

        // Values are null by default
        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write a value for the first time

        final Hash hash1 = randomHash(random);

        assertNull(scratchpad.set(FOO, hash1));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash1, scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        final SerializableLong long1 = new SerializableLong(random.nextLong());
        assertNull(scratchpad.set(BAR, long1));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash1, scratchpad.get(FOO));
        assertEquals(long1, scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        final NodeId nodeId1 = new NodeId(random.nextInt(0, 1000));
        assertNull(scratchpad.set(BAZ, nodeId1));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash1, scratchpad.get(FOO));
        assertEquals(long1, scratchpad.get(BAR));
        assertEquals(nodeId1, scratchpad.get(BAZ));

        // Overwrite an existing value

        final Hash hash2 = randomHash(random);
        assertEquals(hash1, scratchpad.set(FOO, hash2));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash2, scratchpad.get(FOO));
        assertEquals(long1, scratchpad.get(BAR));
        assertEquals(nodeId1, scratchpad.get(BAZ));

        final SerializableLong long2 = new SerializableLong(random.nextLong());
        assertEquals(long1, scratchpad.set(BAR, long2));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash2, scratchpad.get(FOO));
        assertEquals(long2, scratchpad.get(BAR));
        assertEquals(nodeId1, scratchpad.get(BAZ));

        final NodeId nodeId2 = new NodeId(random.nextInt(1001, 2000));
        assertEquals(nodeId1, scratchpad.set(BAZ, nodeId2));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash2, scratchpad.get(FOO));
        assertEquals(long2, scratchpad.get(BAR));
        assertEquals(nodeId2, scratchpad.get(BAZ));

        // Clear the scratchpad

        scratchpad.clear();
        assertNull(scratchpadDirectory.toFile().listFiles());
        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write after a clear

        final Hash hash3 = randomHash(random);
        assertNull(scratchpad.set(FOO, hash3));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash3, scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        final SerializableLong long3 = new SerializableLong(random.nextLong());
        assertNull(scratchpad.set(BAR, long3));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash3, scratchpad.get(FOO));
        assertEquals(long3, scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        final NodeId nodeId3 = new NodeId(random.nextInt(2001, 3000));
        assertNull(scratchpad.set(BAZ, nodeId3));
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash3, scratchpad.get(FOO));
        assertEquals(long3, scratchpad.get(BAR));
        assertEquals(nodeId3, scratchpad.get(BAZ));

        // Simulate a restart
        final Scratchpad<TestScratchpadType> scratcphad2 =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
        assertEquals(hash3, scratcphad2.get(FOO));
        assertEquals(long3, scratcphad2.get(BAR));
        assertEquals(nodeId3, scratcphad2.get(BAZ));
    }

    /**
     * This test simulates a crash between the copy of the next scratchpad file and the deletion of the previous
     * scratchpad file.
     */
    @Test
    @DisplayName("Multiple Files Test")
    void multipleFilesTest() throws IOException {
        final Random random = getRandomPrintSeed();

        final Scratchpad<TestScratchpadType> scratchpad =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        final Path scratchpadDirectory =
                testDirectory.resolve("scratchpad").resolve("0").resolve("test");

        // No scratchpad file will exist until we write the first value
        assertFalse(Files.exists(scratchpadDirectory));

        // Values are null by default
        assertNull(scratchpad.get(FOO));

        final Hash hash1 = randomHash(random);
        scratchpad.set(FOO, hash1);
        assertEquals(hash1, scratchpad.get(FOO));

        // After a write, there should always be exactly one scratchpad file
        final File[] files = scratchpadDirectory.toFile().listFiles();
        assertEquals(1, files.length);

        // Make a copy of that file
        final Path scratchpadFile = files[0].toPath();
        final Path copyPath = testDirectory.resolve(scratchpadFile.getFileName());
        Files.copy(scratchpadFile, copyPath);

        final Hash hash2 = randomHash(random);
        scratchpad.set(FOO, hash2);

        // After a write, there should always be exactly one scratchpad file
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        // Copy the file back, simulating a crash
        Files.copy(copyPath, scratchpadFile);

        // Simulate a restart
        final Scratchpad scratchpad2 = Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");

        assertEquals(hash2, scratchpad2.get(FOO));

        // The extra file should have been cleaned up on restart
        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);
    }

    @Test
    @DisplayName("Atomic Operation Test")
    void atomicOperationTest() {
        final Random random = getRandomPrintSeed();

        final Scratchpad<TestScratchpadType> scratchpad =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        final Path scratchpadDirectory =
                testDirectory.resolve("scratchpad").resolve("0").resolve("test");

        // No scratchpad file will exist until we write the first value
        assertFalse(scratchpadDirectory.toFile().exists());

        // Values are null by default
        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write a value for the first time

        final Hash hash1 = randomHash(random);
        final SerializableLong long1 = new SerializableLong(random.nextLong());
        final NodeId nodeId1 = new NodeId(random.nextInt(0, 1000));

        scratchpad.atomicOperation(map -> {
            assertNull(map.put(FOO, hash1));
            assertNull(map.put(BAR, long1));
            assertNull(map.put(BAZ, nodeId1));
        });

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash1, scratchpad.get(FOO));
        assertEquals(long1, scratchpad.get(BAR));
        assertEquals(nodeId1, scratchpad.get(BAZ));

        // Overwrite an existing value

        final Hash hash2 = randomHash(random);
        final SerializableLong long2 = new SerializableLong(random.nextLong());
        final NodeId nodeId2 = new NodeId(random.nextInt(1001, 2000));

        scratchpad.atomicOperation(map -> {
            assertEquals(hash1, map.put(FOO, hash2));
            assertEquals(long1, map.put(BAR, long2));
            assertEquals(nodeId1, map.put(BAZ, nodeId2));
        });

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash2, scratchpad.get(FOO));
        assertEquals(long2, scratchpad.get(BAR));
        assertEquals(nodeId2, scratchpad.get(BAZ));

        // Clear the scratchpad

        scratchpad.clear();
        assertNull(scratchpadDirectory.toFile().listFiles());

        scratchpad.atomicOperation(map -> {
            assertNull(scratchpad.get(FOO));
            assertNull(scratchpad.get(BAR));
            assertNull(scratchpad.get(BAZ));
        });

        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write after a clear

        final Hash hash3 = randomHash(random);
        final SerializableLong long3 = new SerializableLong(random.nextLong());
        final NodeId nodeId3 = new NodeId(random.nextInt(2001, 3000));

        scratchpad.atomicOperation(map -> {
            assertNull(map.put(FOO, hash3));
            assertNull(map.put(BAR, long3));
            assertNull(map.put(BAZ, nodeId3));
        });

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash3, scratchpad.get(FOO));
        assertEquals(long3, scratchpad.get(BAR));
        assertEquals(nodeId3, scratchpad.get(BAZ));

        // Simulate a restart
        final Scratchpad<TestScratchpadType> scratcphad2 =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        scratcphad2.atomicOperation(map -> {
            assertEquals(hash3, map.get(FOO));
            assertEquals(long3, map.get(BAR));
            assertEquals(nodeId3, map.get(BAZ));
        });

        assertEquals(hash3, scratcphad2.get(FOO));
        assertEquals(long3, scratcphad2.get(BAR));
        assertEquals(nodeId3, scratcphad2.get(BAZ));
    }

    @Test
    void optionalAtomicOperationTest() {
        final Random random = getRandomPrintSeed();

        final Scratchpad<TestScratchpadType> scratchpad =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        final Path scratchpadDirectory =
                testDirectory.resolve("scratchpad").resolve("0").resolve("test");

        // No scratchpad file will exist until we write the first value
        assertFalse(scratchpadDirectory.toFile().exists());

        // Values are null by default
        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write a value for the first time

        final Hash hash1 = randomHash(random);
        final SerializableLong long1 = new SerializableLong(random.nextLong());
        final NodeId nodeId1 = new NodeId(random.nextInt(0, 1000));

        scratchpad.atomicOperation(map -> {
            assertNull(map.put(FOO, hash1));
            assertNull(map.put(BAR, long1));
            assertNull(map.put(BAZ, nodeId1));

            return true;
        });

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash1, scratchpad.get(FOO));
        assertEquals(long1, scratchpad.get(BAR));
        assertEquals(nodeId1, scratchpad.get(BAZ));

        // Overwrite an existing value

        final Hash hash2 = randomHash(random);
        final SerializableLong long2 = new SerializableLong(random.nextLong());
        final NodeId nodeId2 = new NodeId(random.nextInt(1001, 2000));

        scratchpad.atomicOperation(map -> {
            assertEquals(hash1, map.put(FOO, hash2));
            assertEquals(long1, map.put(BAR, long2));
            assertEquals(nodeId1, map.put(BAZ, nodeId2));

            return true;
        });

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash2, scratchpad.get(FOO));
        assertEquals(long2, scratchpad.get(BAR));
        assertEquals(nodeId2, scratchpad.get(BAZ));

        // Clear the scratchpad

        scratchpad.clear();
        assertNull(scratchpadDirectory.toFile().listFiles());

        scratchpad.atomicOperation(map -> {
            assertNull(scratchpad.get(FOO));
            assertNull(scratchpad.get(BAR));
            assertNull(scratchpad.get(BAZ));

            return true;
        });

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertNull(scratchpad.get(FOO));
        assertNull(scratchpad.get(BAR));
        assertNull(scratchpad.get(BAZ));

        // Write after a clear

        final Hash hash3 = randomHash(random);
        final SerializableLong long3 = new SerializableLong(random.nextLong());
        final NodeId nodeId3 = new NodeId(random.nextInt(2001, 3000));

        scratchpad.atomicOperation(map -> {
            assertNull(map.put(FOO, hash3));
            assertNull(map.put(BAR, long3));
            assertNull(map.put(BAZ, nodeId3));

            return true;
        });

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        assertEquals(hash3, scratchpad.get(FOO));
        assertEquals(long3, scratchpad.get(BAR));
        assertEquals(nodeId3, scratchpad.get(BAZ));

        // Simulate a restart
        final Scratchpad<TestScratchpadType> scratcphad2 =
                Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "test");
        scratchpad.logContents();

        assertEquals(1, scratchpadDirectory.toFile().listFiles().length);

        scratcphad2.atomicOperation(map -> {
            assertEquals(hash3, map.get(FOO));
            assertEquals(long3, map.get(BAR));
            assertEquals(nodeId3, map.get(BAZ));

            return true;
        });

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertEquals(hash3, scratcphad2.get(FOO));
        assertEquals(long3, scratcphad2.get(BAR));
        assertEquals(nodeId3, scratcphad2.get(BAZ));

        // Should have no effect.
        scratchpad.atomicOperation(map -> false);

        assertEquals(hash3, scratcphad2.get(FOO));
        assertEquals(long3, scratcphad2.get(BAR));
        assertEquals(nodeId3, scratcphad2.get(BAZ));
    }

    @Test
    @DisplayName("Illegal Scratchpad Id Test")
    void illegalScratchpadIdTest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Scratchpad.create(platformContext, selfId, TestScratchpadType.class, ""));

        assertThrows(
                IllegalArgumentException.class,
                () -> Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "foobar/baz"));

        assertThrows(
                IllegalArgumentException.class,
                () -> Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "foobar\\baz"));

        assertThrows(
                IllegalArgumentException.class,
                () -> Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "foobar\"baz"));

        assertThrows(
                IllegalArgumentException.class,
                () -> Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "foobar*baz"));

        // should not throw
        Scratchpad.create(platformContext, selfId, TestScratchpadType.class, "foo.bar_baz-1234");
    }
}
