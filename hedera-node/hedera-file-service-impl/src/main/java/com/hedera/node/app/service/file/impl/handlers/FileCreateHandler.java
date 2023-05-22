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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateAndAddRequiredKeys;
import static com.hedera.node.app.service.file.impl.utils.FileServiceUtils.validateContent;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.impl.WritableFileStoreImpl;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FilesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_CREATE}.
 */
@Singleton
public class FileCreateHandler implements TransactionHandler {
    @Inject
    public FileCreateHandler() {
        // Exists for injection
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for create a file
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final var transactionBody = context.body().fileCreateOrThrow();

        validateAndAddRequiredKeys(transactionBody.keys(), context, false);

        if (!transactionBody.hasExpirationTime()) {
            throw new PreCheckException(INVALID_EXPIRATION_TIME);
        }
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
            @NonNull final HandleContext handleContext,
            @NonNull final FileCreateTransactionBody fileCreateTransactionBody,
            @NonNull final CreateFileRecordBuilder recordBuilder,
            @NonNull final WritableFileStoreImpl fileStore) {
        requireNonNull(handleContext);
        requireNonNull(fileCreateTransactionBody);
        requireNonNull(recordBuilder);
        requireNonNull(fileStore);

        final var builder = new File.Builder();
        final var fileServiceConfig = handleContext.getConfiguration().getConfigData(FilesConfig.class);

        if (fileCreateTransactionBody.hasKeys()) {
            builder.keys(fileCreateTransactionBody.keys());
        }

        /* Validate if the current file can be created */
        if (fileStore.sizeOfState() >= fileServiceConfig.maxNumber()) {
            throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        }

        var expiry = fileCreateTransactionBody.hasExpirationTime()
                ? fileCreateTransactionBody.expirationTimeOrThrow().seconds()
                : NA;
        final var entityExpiryMeta = new ExpiryMeta(
                expiry,
                NA,
                // Shard and realm will be ignored if num is NA
                NA,
                NA,
                NA);

        try {
            final var effectiveExpiryMeta =
                    handleContext.expiryValidator().resolveCreationAttempt(false, entityExpiryMeta);
            builder.expirationTime(effectiveExpiryMeta.expiry());

            handleContext.attributeValidator().validateMemo(fileCreateTransactionBody.memo());
            builder.memo(fileCreateTransactionBody.memo());

            builder.keys(fileCreateTransactionBody.keys());
            builder.fileNumber(handleContext.newEntityNumSupplier().getAsLong());
            validateContent(PbjConverter.asBytes(fileCreateTransactionBody.contents()), fileServiceConfig);
            builder.contents(fileCreateTransactionBody.contents());

            final var file = builder.build();
            fileStore.put(file);

            recordBuilder.setCreatedFile(file.fileNumber());
        } catch (final HandleException e) {
            if (e.getStatus() == INVALID_EXPIRATION_TIME) {
                // Since for some reason CreateTransactionBody does not have an expiration time,
                // it makes more sense to propagate AUTORENEW_DURATION_NOT_IN_RANGE
                throw new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            throw e;
        }
    }

    @Override
    public CreateFileRecordBuilder newRecordBuilder() {
        return new CreateFileRecordBuilder();
    }
}
