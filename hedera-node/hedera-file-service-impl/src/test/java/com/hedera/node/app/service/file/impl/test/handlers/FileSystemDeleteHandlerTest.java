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

package com.hedera.node.app.service.file.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.file.impl.test.handlers.FileTestUtils.mockFileLookup;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.records.DeleteFileRecordBuilder;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSystemDeleteHandlerTest extends FileHandlerTestBase {

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableFileStoreImpl mockStore;

    @Mock
    private FileSystemDeleteHandler subject;

    @Mock
    private HandleContext handleContext;

    @Mock
    private Instant instant;

    @Mock
    private Configuration configuration;

    private FilesConfig config;

    @BeforeEach
    void setUp() {
        mockStore = mock(ReadableFileStoreImpl.class);
        subject = new FileSystemDeleteHandler();

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);
        config = new FilesConfig(101L, 121L, 112L, 111L, 122L, 102L, 123L, 1000000L, 1024);
        lenient().when(handleContext.getConfiguration()).thenReturn(configuration);
        lenient().when(configuration.getConfigData(FilesConfig.class)).thenReturn(config);
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(DeleteFileRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("File not found returns error")
    void fileIdNotFound() throws PreCheckException {
        // given:
        mockPayerLookup();
        given(mockStore.getFileMetadata(notNull())).willReturn(null);
        final var context = new FakePreHandleContext(accountStore, newSystemDeleteTxn());
        context.registerStore(ReadableFileStoreImpl.class, mockStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_FILE_ID);
    }

    @Test
    @DisplayName("File without keys returns error")
    void noFileKeys() throws PreCheckException {
        // given:
        mockPayerLookup();
        mockFileLookup(null, mockStore);
        final var context = new FakePreHandleContext(accountStore, newSystemDeleteTxn());
        context.registerStore(ReadableFileStoreImpl.class, mockStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context), UNAUTHORIZED);
    }

    @Test
    @DisplayName("Fails handle if file doesn't exist")
    void fileDoesntExist() {
        final var txn = newSystemDeleteTxn().systemDeleteOrThrow();

        writableFileState = emptyWritableFileState();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext, txn, writableStore));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if the file is not a system file")
    void fileIsNotSystemFile() {
        final var txn = newFileDeleteTxn().systemDeleteOrThrow();

        final var existingFile = writableStore.get(fileEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext, txn, writableStore));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if keys doesn't exist on file system to be deleted")
    void keysDoesntExist() {
        final var txn = newSystemDeleteTxn().systemDeleteOrThrow();
        fileSystem = new File(fileSystemfileId.fileNum(), expirationTime, null, Bytes.wrap(contents), memo, false);

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext, txn, writableStore));

        assertEquals(UNAUTHORIZED, msg.getStatus());
    }

    @Test
    @DisplayName("Handle works as expected and file system deleted when time is expired(less than epoch second)")
    void handleWorksAsExpectedWhenExpirationTimeIsExpired() {
        final var txn = newSystemDeleteTxn().systemDeleteOrThrow();

        final var existingFile = writableStore.get(fileSystemEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationTime() + 100);
        subject.handle(handleContext, txn, writableStore);

        final var changedFile = writableStore.get(fileSystemEntityNum.longValue());

        assertEquals(changedFile, Optional.empty());
    }

    @Test
    @DisplayName("Handle works as expected and the system file marked as deleted")
    void handleWorksAsExpectedWhenExpirationTimeIsNotExpired() {
        final var txn = newSystemDeleteTxn().systemDeleteOrThrow();

        final var existingFile = writableStore.get(fileSystemEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationTime() - 100);
        subject.handle(handleContext, txn, writableStore);

        final var changedFile = writableStore.get(fileSystemEntityNum.longValue());

        assertTrue(changedFile.isPresent());
        assertTrue(changedFile.get().deleted());
    }

    private Key mockPayerLookup() throws PreCheckException {
        return FileTestUtils.mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
    }

    private TransactionBody newSystemDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemDeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_SYSTEM_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemDelete(deleteFileSystemBuilder.build())
                .build();
    }

    private TransactionBody newFileDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemDeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemDelete(deleteFileSystemBuilder.build())
                .build();
    }
}
