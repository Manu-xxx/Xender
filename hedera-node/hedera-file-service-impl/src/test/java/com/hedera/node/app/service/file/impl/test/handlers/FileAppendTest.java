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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.WritableUpgradeFileStore;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileAppendTest extends FileTestBase {
    private final FileAppendTransactionBody.Builder OP_BUILDER = FileAppendTransactionBody.newBuilder();

    @Mock
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private PreHandleContext preHandleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected TransactionDispatcher mockDispatcher;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStoreFactory mockStoreFactory;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account payerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    HederaFileNumbers fileNumbers;

    @Mock(strictness = Mock.Strictness.LENIENT)
    FeeCalculator feeCalculator;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private FeeAccumulator feeAccumulator;

    protected Configuration testConfig;

    private FileAppendHandler subject;

    @BeforeEach
    void setUp() {
        subject = new FileAppendHandler(fileNumbers);
        testConfig = HederaTestConfigBuilder.createConfig();
        when(preHandleContext.configuration()).thenReturn(testConfig);
        when(handleContext.configuration()).thenReturn(testConfig);
        when(handleContext.feeCalculator(any(SubType.class))).thenReturn(feeCalculator);
        when(feeCalculator.calculate()).thenReturn(Fees.FREE);
        when(feeCalculator.addBytesPerTransaction(anyLong())).thenReturn(feeCalculator);
        when(feeCalculator.addStorageBytesSeconds(anyLong())).thenReturn(feeCalculator);
        when(handleContext.feeAccumulator()).thenReturn(feeAccumulator);
        when(feeCalculator.legacyCalculate(any())).thenReturn(Fees.FREE);
    }

    @Test
    void rejectsMissingFile() {
        final var txBody = TransactionBody.newBuilder().fileAppend(OP_BUILDER).build();
        when(handleContext.body()).thenReturn(txBody);

        // expect:
        assertFailsWith(INVALID_FILE_ID, () -> subject.handle(handleContext));
    }

    @Test
    @DisplayName("Pre handle works as expected")
    void preHandleWorksAsExpected() throws PreCheckException {
        refreshStoresWithCurrentFileOnlyInReadable();

        BDDMockito.given(accountStore.getAccountById(payerId)).willReturn(payerAccount);
        BDDMockito.given(mockStoreFactory.getStore(ReadableFileStore.class)).willReturn(readableStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
        BDDMockito.given(payerAccount.key()).willReturn(A_COMPLEX_KEY);

        // No-op
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .transactionID(txnId)
                .build();

        PreHandleContext realPreContext =
                new PreHandleContextImpl(mockStoreFactory, txBody, testConfig, mockDispatcher);

        subject.preHandle(realPreContext);

        assertTrue(realPreContext.requiredNonPayerKeys().size() > 0);
        assertEquals(3, realPreContext.requiredNonPayerKeys().size());
    }

    @Test
    @DisplayName("Pre handle works as expected immutable")
    void preHandleWorksAsExpectedImmutable() throws PreCheckException {
        file = createFileEmptyMemoAndKeys();
        refreshStoresWithCurrentFileOnlyInReadable();
        BDDMockito.given(accountStore.getAccountById(payerId)).willReturn(payerAccount);
        BDDMockito.given(mockStoreFactory.getStore(ReadableFileStore.class)).willReturn(readableStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
        BDDMockito.given(payerAccount.key()).willReturn(A_COMPLEX_KEY);

        // No-op
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .transactionID(txnId)
                .build();
        PreHandleContext realPreContext =
                new PreHandleContextImpl(mockStoreFactory, txBody, testConfig, mockDispatcher);

        subject.preHandle(realPreContext);

        assertEquals(0, realPreContext.requiredNonPayerKeys().size());
    }

    @Test
    void rejectsDeletedFile() {
        givenValidFile(true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();
        when(handleContext.body()).thenReturn(txBody);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(false);

        // expect:
        assertFailsWith(FILE_DELETED, () -> subject.handle(handleContext));
    }

    @Test
    void validatesNewContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(Bytes.wrap(new byte[1048577])))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(false);
        // expect:
        assertFailsWith(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED, () -> subject.handle(handleContext));
    }

    @Test
    void validatesNewContentEmptyRemainSameContent() {
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(Bytes.wrap(new byte[0])))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(false);

        // expect:
        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(file.contents(), appendedFile.contents());
    }

    @Test
    void appliesNewContent() {
        final var additionalContent = "STUFF".getBytes();
        var bytesNewContent = Bytes.wrap(additionalContent);
        givenValidFile(false);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        var newContent = ArrayUtils.addAll(contents, additionalContent);
        var bytesNewContentExpected = Bytes.wrap(newContent);
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(bytesNewContent))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(false);

        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(bytesNewContentExpected, appendedFile.contents());
    }

    @Test
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        // No-op
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(false);

        subject.handle(handleContext);

        final var appendedFile = writableFileState.get(fileId);
        assertEquals(file, appendedFile);
    }

    public static void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    @Test
    void appliesUpgradeFileNewContent() {
        final var additionalContent = "contents".getBytes();
        var bytesNewContent = Bytes.wrap(additionalContent);
        givenValidUpgradeFile(false, true);
        refreshStoresWithCurrentFileInBothReadableAndWritable();

        var bytesNewContentExpected = new ProtoBytes(Bytes.wrap(additionalContent));
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnowUpgradeId()).contents(bytesNewContent))
                .build();
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.writableStore(WritableUpgradeFileStore.class)).willReturn(writableUpgradeFileStore);

        subject.handle(handleContext);
        final var iterator = writableUpgradeStates.iterator();
        ProtoBytes file = null;

        while (iterator.hasNext()) {
            file = iterator.next();
        }
        assertEquals(bytesNewContentExpected, file);
    }

    @Test
    @DisplayName("Fails handle if keys doesn't exist on file to be appended")
    void failsForImmutableFile() {
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()))
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(111111).build())
                        .build())
                .build();

        file = new File(fileId, expirationTime, null, Bytes.wrap(contents), memo, false);

        given(handleContext.body()).willReturn(txBody);
        writableFileState = writableFileStateWithOneKey();
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        writableStore = new WritableFileStore(writableStates);
        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);

        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        assertEquals(ResponseCodeEnum.UNAUTHORIZED, msg.getStatus());
    }

    @Test
    void handleThrowsIfKeysSignatureFailed() {
        final var txBody = TransactionBody.newBuilder()
                .fileAppend(OP_BUILDER.fileID(wellKnownId()).contents(Bytes.wrap(new byte[1048577])))
                .build();

        given(handleContext.body()).willReturn(txBody);
        given(handleContext.verificationFor(Mockito.any(Key.class))).willReturn(signatureVerification);
        given(signatureVerification.failed()).willReturn(true);

        assertThrows(HandleException.class, () -> subject.handle(handleContext));
    }

    private FileID wellKnownId() {
        return fileId;
    }

    private FileID wellKnowUpgradeId() {

        return fileUpgradeFileId;
    }
}
