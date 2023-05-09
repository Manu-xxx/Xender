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

package com.hedera.node.app.service.file.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_CONTENT_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.file.impl.utils.FileUtils.preValidate;
import static com.hedera.node.app.service.file.impl.utils.FileUtils.validateContent;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.config.FileServiceConfig;
import com.hedera.node.app.service.file.impl.records.UpdateFileRecordBuilder;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_APPEND}.
 */
@Singleton
public class FileAppendHandler implements TransactionHandler {
    private static final Logger logger = LogManager.getLogger(FileAppendHandler.class);

    @Inject
    public FileAppendHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for append a file
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if any issue happens on the pre handle level
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().fileAppendOrThrow();
        final var fileStore = context.createStore(ReadableFileStoreImpl.class);
        final var fileMeta = preValidate(transactionBody, fileStore);

        final var listKeys = fileMeta.keys();
        if (listKeys == null || !listKeys.hasKeys() || listKeys.keys().isEmpty()) {
            throw new PreCheckException(UNAUTHORIZED);
        }

        final var candidate = Key.newBuilder().keyList(listKeys).build();
        context.requireKey(candidate);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final FileAppendTransactionBody op,
            @NonNull final WritableFileStoreImpl fileStore,
            @NonNull final FileServiceConfig fileServiceConfig) {
        requireNonNull(op);
        requireNonNull(fileStore);
        requireNonNull(fileServiceConfig);

        final var target = op.fileID();
        final var data = op.contents();
        if (data == null || data.length() <= 0) {
            logger.info("FileAppend: No data to append");
        }

        if (target == null) {
            throw new HandleException(INVALID_FILE_ID);
        }
        final var optionalFile = fileStore.get(target.fileNum());

        if (optionalFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }
        final var file = optionalFile.get();

        if (file.deleted()) {
            throw new HandleException(FILE_DELETED);
        }

        var contents = PbjConverter.asBytes(file.contents());

        if (data == null) {
            throw new HandleException(FILE_CONTENT_EMPTY);
        }
        var newContents = ArrayUtils.addAll(contents, PbjConverter.asBytes(data));
        validateContent(newContents, fileServiceConfig);
        /* Copy all the fields from existing file and change deleted flag */
        final var fileBuilder = new File.Builder()
                .fileNumber(file.fileNumber())
                .expirationTime(file.expirationTime())
                .keys(file.keys())
                .contents(Bytes.wrap(newContents))
                .memo(file.memo())
                .deleted(file.deleted());

        /* --- Put the modified file. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        fileStore.put(fileBuilder.build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpdateFileRecordBuilder newRecordBuilder() {
        return new UpdateFileRecordBuilder();
    }
}
