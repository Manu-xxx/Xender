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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
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
class FileSystemUndeleteHandlerTest extends FileHandlerTestBase {

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableFileStoreImpl mockStore;

    @Mock
    private FileSystemUndeleteHandler subject;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private PreHandleContext preHandleContext;

    @Mock
    private Instant instant;

    @Mock
    private Configuration configuration;

    private FilesConfig config;

    @BeforeEach
    void setUp() {
        mockStore = mock(ReadableFileStoreImpl.class);
        subject = new FileSystemUndeleteHandler();

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        lenient().when(preHandleContext.configuration()).thenReturn(configuration);
        lenient().when(handleContext.configuration()).thenReturn(configuration);
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
        lenient().when(preHandleContext.body()).thenReturn(newSystemDeleteTxn());
        lenient()
                .when(preHandleContext.createStore(ReadableFileStoreImpl.class))
                .thenReturn(mockStore);

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), UNAUTHORIZED);
    }

    @Test
    @DisplayName("Fails handle if file doesn't exist")
    void fileDoesntExist() {
        final var txn = newSystemDeleteTxn().systemUndeleteOrThrow();

        writableFileState = emptyWritableFileState();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().systemUndelete(txn).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if the file is not a system file")
    void fileIsNotSystemFile() {
        final var txn = newFileDeleteTxn().systemUndeleteOrThrow();

        final var existingFile = writableStore.get(fileEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().systemUndelete(txn).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_FILE_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if keys doesn't exist on file system to be deleted")
    void keysDoesntExist() {
        final var txn = newSystemDeleteTxn().systemUndeleteOrThrow();
        fileSystem = new File(fileSystemfileId.fileNum(), expirationTime, null, Bytes.wrap(contents), memo, false);

        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<EntityNum, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStoreImpl(writableStates);
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().systemUndelete(txn).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        assertEquals(UNAUTHORIZED, msg.getStatus());
    }

    @Test
    @DisplayName("Handle works as expected and file system deleted when time is expired(less than epoch second)")
    void handleWorksAsExpectedWhenExpirationTimeIsExpired() {
        final var txn = newSystemDeleteTxn().systemUndeleteOrThrow();

        final var existingFile = writableStore.get(fileSystemEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationTime() + 100);
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().systemUndelete(txn).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileSystemEntityNum.longValue());

        assertEquals(changedFile, Optional.empty());
    }

    @Test
    @DisplayName("Handle works as expected and the system file marked as undeleted")
    void handleWorksAsExpectedWhenExpirationTimeIsNotExpired() {
        final var txn = newSystemDeleteTxn().systemUndeleteOrThrow();

        final var existingFile = writableStore.get(fileSystemEntityNum.longValue());
        assertTrue(existingFile.isPresent());
        assertFalse(existingFile.get().deleted());

        lenient().when(handleContext.consensusNow()).thenReturn(instant);
        lenient().when(instant.getEpochSecond()).thenReturn(existingFile.get().expirationTime() - 100);
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().systemUndelete(txn).build());
        given(handleContext.writableStore(WritableFileStoreImpl.class)).willReturn(writableStore);
        subject.handle(handleContext);

        final var changedFile = writableStore.get(fileSystemEntityNum.longValue());

        assertTrue(changedFile.isPresent());
        assertFalse(changedFile.get().deleted());
    }

    private Key mockPayerLookup() throws PreCheckException {
        return FileTestUtils.mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
    }

    private TransactionBody newSystemDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemUndeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_SYSTEM_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemUndelete(deleteFileSystemBuilder.build())
                .build();
    }

    private TransactionBody newFileDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var deleteFileSystemBuilder =
                SystemUndeleteTransactionBody.newBuilder().fileID(WELL_KNOWN_FILE_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .systemUndelete(deleteFileSystemBuilder.build())
                .build();
    }
}
