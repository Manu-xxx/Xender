/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.throwIfFileExists;
import static com.swirlds.platform.state.PlatformStateAccessor.PLATFORM_NAME;
import static com.swirlds.platform.state.PlatformStateAccessor.PLATFORM_STATE_KEY;
import static com.swirlds.platform.state.snapshot.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.CURRENT_ADDRESS_BOOK_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.HASH_INFO_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileUtils.SIGNED_STATE_FILE_NAME;
import static com.swirlds.platform.state.snapshot.SignedStateFileWriter.writeHashInfoFile;
import static com.swirlds.platform.state.snapshot.SignedStateFileWriter.writeSignedStateToDisk;
import static com.swirlds.platform.state.snapshot.SignedStateFileWriter.writeStateFile;
import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.config.StateCommonConfig_;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.LegacyTemporaryFileBuilder;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.snapshot.DeserializedSignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.state.snapshot.StateToDiskReason;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.test.fixtures.state.NoOpMerkleStateLifecycles;
import com.swirlds.state.merkle.StateUtils;
import com.swirlds.state.merkle.singleton.SingletonNode;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SignedState Read/Write Test")
class SignedStateFileReadWriteTest {
    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private static SemanticVersion platformVersion;
    private SignedState signedState;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        final var registry = ConstructableRegistry.getInstance();
        platformVersion =
                SemanticVersion.newBuilder().major(RandomUtils.nextInt(1, 100)).build();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.platform");
        registry.registerConstructables("com.swirlds.state");
        registry.registerConstructable(new ClassConstructorPair(
                MerkleStateRoot.class,
                () -> new MerkleStateRoot(
                        new NoOpMerkleStateLifecycles(), version -> new BasicSoftwareVersion(version.major()))));
        registry.registerConstructable(new ClassConstructorPair(
                SingletonNode.class,
                () -> new SingletonNode<>(
                        PLATFORM_NAME,
                        PLATFORM_STATE_KEY,
                        StateUtils.computeClassId(PLATFORM_NAME, PLATFORM_STATE_KEY, platformVersion, "SingletonLeaf"),
                        PlatformState.PROTOBUF,
                        null)));
    }

    @BeforeEach
    void beforeEach() throws IOException {
        LegacyTemporaryFileBuilder.overrideTemporaryFileLocation(testDirectory.resolve("tmp"));
        signedState = new RandomSignedStateGenerator()
                .setSoftwareVersion(new BasicSoftwareVersion(platformVersion.minor()))
                .build();
    }

    @Test
    @DisplayName("writeHashInfoFile() Test")
    void writeHashInfoFileTest() throws IOException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        MerkleRoot state = signedState.getState();
        writeHashInfoFile(platformContext, testDirectory, state);
        final StateConfig stateConfig =
                new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class);

        final Path hashInfoFile = testDirectory.resolve(SignedStateFileUtils.HASH_INFO_FILE_NAME);
        assertTrue(exists(hashInfoFile), "file should exist");

        final String hashInfoString = new MerkleTreeVisualizer(state)
                .setDepth(stateConfig.debugHashDepth())
                .render();

        final StringBuilder sb = new StringBuilder();
        try (final BufferedReader br = new BufferedReader(new FileReader(hashInfoFile.toFile()))) {
            br.lines().forEach(line -> sb.append(line).append("\n"));
        }

        final String fileString = sb.toString();
        assertTrue(fileString.contains(hashInfoString), "hash info string not found");
    }

    @Test
    @DisplayName("Write Then Read State File Test")
    void writeThenReadStateFileTest() throws IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        final Path stateFile = testDirectory.resolve(SIGNED_STATE_FILE_NAME);

        assertFalse(exists(stateFile), "signed state file should not yet exist");
        writeStateFile(testDirectory, signedState);
        assertTrue(exists(stateFile), "signed state file should be present");

        final DeserializedSignedState deserializedSignedState =
                readStateFile(TestPlatformContextBuilder.create().build(), stateFile, SignedStateFileUtils::readState);
        MerkleCryptoFactory.getInstance()
                .digestTreeSync(
                        deserializedSignedState.reservedSignedState().get().getState());

        assertNotNull(deserializedSignedState.originalHash(), "hash should not be null");
        assertEquals(signedState.getState().getHash(), deserializedSignedState.originalHash(), "hash should match");
        assertEquals(
                signedState.getState().getHash(),
                deserializedSignedState.reservedSignedState().get().getState().getHash(),
                "hash should match");
        assertNotSame(signedState, deserializedSignedState.reservedSignedState(), "state should be a different object");
    }

    @Test
    @DisplayName("writeSavedStateToDisk() Test")
    void writeSavedStateToDiskTest() throws IOException {
        final Path directory = testDirectory.resolve("state");

        final Path stateFile = directory.resolve(SIGNED_STATE_FILE_NAME);
        final Path hashInfoFile = directory.resolve(HASH_INFO_FILE_NAME);
        final Path settingsUsedFile = directory.resolve("settingsUsed.txt");
        final Path addressBookFile = directory.resolve(CURRENT_ADDRESS_BOOK_FILE_NAME);

        throwIfFileExists(stateFile, hashInfoFile, settingsUsedFile, directory);
        final String configDir = testDirectory.resolve("data/saved").toString();
        final Configuration configuration = changeConfigAndConfigHolder(configDir);

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        writeSignedStateToDisk(
                platformContext, new NodeId(0), directory, signedState, StateToDiskReason.PERIODIC_SNAPSHOT);

        assertTrue(exists(stateFile), "state file should exist");
        assertTrue(exists(hashInfoFile), "hash info file should exist");
        assertTrue(exists(settingsUsedFile), "settings used file should exist");
        assertTrue(exists(addressBookFile), "address book file should exist");
    }

    private Configuration changeConfigAndConfigHolder(String directory) {
        return new TestConfigBuilder()
                .withValue(StateCommonConfig_.SAVED_STATE_DIRECTORY, directory)
                .getOrCreateConfig();
    }
}
