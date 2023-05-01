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

package com.hedera.services.cli.sign.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.cli.sign.RecordStreamSignCommand;
import com.hedera.services.cli.sign.RecordStreamType;
import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;

class RecordStreamSignCommandTest {
    @Mock
    PublicKey publicKey;

    @Mock
    PrivateKey privateKey;

    @TempDir
    private File tmpDir;

    @Test
    @DisplayName("Failure to generate signature file for file record stream")
    void failureGenerateSignatureFileRecordStream() {
        // given:
        final var signatureFileDestination = Path.of("testPath");
        final Path fileToSign;
        fileToSign = signatureFileDestination;
        final var keyPair = new KeyPair(publicKey, privateKey);
        final var hapiVersion = "0.37.0-allowance-SNAPSHOT";
        // when:
        final var recordStreamSignCommand = new RecordStreamSignCommand();
        recordStreamSignCommand.setHapiVersion(hapiVersion);

        // then:
        assertFalse(recordStreamSignCommand.generateSignatureFile(signatureFileDestination, fileToSign, keyPair));
    }

    @Test
    @DisplayName("Succeed to generate signature file for file record stream")
    void succeedToGenerateSignatureFileRecordStream() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2023-04-18T14_08_20.465612003Z.rcd_sig");
        final var fileToSign = Path.of(Objects.requireNonNull(AccountBalanceSignCommandTest.class
                        .getClassLoader()
                        .getResource("com.hedera.services.cli.sign.test/2023-04-18T14_08_20.465612003Z.rcd"))
                .getPath());

        // when:
        final var recordStreamSignCommand = new RecordStreamSignCommand();
        recordStreamSignCommand.setHapiVersion("0.37.0-allowance-SNAPSHOT");

        // then:
        assertTrue(recordStreamSignCommand.generateSignatureFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("Succeed to generate signature file for gz file record stream")
    void succeedToGenerateSignatureFileGzipRecordStream() {
        // given:
        final var signatureFileDestination = Path.of(tmpDir.getPath() + "/2022-09-19T21_09_17.348788413Z.rcd.gz");
        final var fileToSign = Path.of(Objects.requireNonNull(AccountBalanceSignCommandTest.class
                        .getClassLoader()
                        .getResource("com.hedera.services.cli.sign.test/2022-09-19T21_09_17.348788413Z.rcd.gz"))
                .getPath());

        // when:
        final var recordStreamSignCommand = new RecordStreamSignCommand();
        recordStreamSignCommand.setHapiVersion("0.37.0-allowance-SNAPSHOT");

        // then:
        assertTrue(recordStreamSignCommand.generateSignatureFile(
                signatureFileDestination, fileToSign, TestUtils.loadKey()));
    }

    @Test
    @DisplayName("File supported for record stream")
    void isFileSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.rcd");

        // then:
        assertTrue(RecordStreamType.getInstance()
                .isStreamFile(signatureFileDestination.toFile().getName()));
    }

    @Test
    @DisplayName("File supported for gz record stream")
    void isGzFileSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.rcd.gz");

        // then:
        assertTrue(RecordStreamType.getInstance()
                .isGzFile(signatureFileDestination.toFile().getName()));
    }

    @Test
    @DisplayName("File not supported for account balance")
    void isFileNotSupported() {
        // given:
        final var signatureFileDestination = Path.of("testPath.lv");

        // then:
        assertFalse(RecordStreamType.getInstance()
                .isStreamFile(signatureFileDestination.toFile().getName()));
    }
}
