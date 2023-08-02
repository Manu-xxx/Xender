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

package com.hedera.node.app.service.file.impl.utils;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_CONTENT_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/** Provides utility methods for file operations. */
public class FileServiceUtils {

    /**
     * Validates the content of a file that it is not empty and not above max size 1MB.
     *
     * @param content the file content
     * @param fileServiceConfig the file service configuration
     */
    public static void validateContent(@NonNull byte[] content, @NonNull FilesConfig fileServiceConfig) {
        var contentLength = content.length;

        if (contentLength <= 0) {
            throw new HandleException(FILE_CONTENT_EMPTY);
        }

        if (contentLength > fileServiceConfig.maxSizeKb() * 1024L) {
            throw new HandleException(MAX_FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * The function validates that the fileId is non-null, not a reserved system Id, and matches a file in the store.
     *
     * @param fileId the file id to validate and to fetch the metadata
     * @param fileStore the file store to fetch the metadata of specified file id
     * @return the file metadata of specific file id
     * @throws PreCheckException if the file id is invalid or the file does not exist
     */
    public static @NonNull FileMetadata preValidate(
            @Nullable final FileID fileId,
            @NonNull final ReadableFileStore fileStore,
            @NonNull final PreHandleContext context,
            boolean isDelete)
            throws PreCheckException {
        requireNonNull(context);

        if (fileId == null) {
            throw new PreCheckException(INVALID_FILE_ID);
        }

        final var fileMeta = fileStore.getFileMetadata(fileId);
        mustExist(fileMeta, INVALID_FILE_ID);

        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        // we cannot delete system files
        if (fileId.fileNum() <= ledgerConfig.numReservedSystemEntities() && isDelete) {
            throw new PreCheckException(INVALID_FILE_ID);
        }

        return fileMeta;
    }

    /**
     * The function validates the keys and adds them to the context.
     *
     * @param file file that will be checked for required keys
     * @param transactionKeys transaction keys that add to context for required keys.
     * @param context the prehandle context for the transaction.
     */
    public static void validateAndAddRequiredKeys(
            @Nullable final File file,
            @Nullable final KeyList transactionKeys,
            @NonNull final PreHandleContext context) {
        if (file != null) {
            KeyList fileKeyList = file.keys();

            if (fileKeyList != null && fileKeyList.hasKeys()) {
                for (final Key key : fileKeyList.keys()) {
                    context.requireKey(key);
                }
            }
        }

        if (transactionKeys != null && transactionKeys.hasKeys()) {
            for (final Key key : transactionKeys.keys()) {
                context.requireKey(key);
            }
        }
    }

    /**
     * This version of the method requires that the file exist in state and not be deleted. If the
     * file is empty this call will fail with {@link ResponseCodeEnum#INVALID_FILE_ID}. If the
     * special file id is greater than special file config , this call will fail with {@link
     * ResponseCodeEnum#INVALID_FILE_ID}. If the file has no keys or empty list, this call will fail
     * with {@link ResponseCodeEnum#UNAUTHORIZED}. If the file is already deleted, this call will
     * fail with {@link ResponseCodeEnum#FILE_DELETED}.
     *
     * @param ledgerConfig the ledger configuration params that is need in order to verify the max file system number.
     * @param fileStore the file store to fetch the metadata of specified file id
     * @param fileId the file id to validate and to fetch the metadata
     * @param canBeDeleted flag indicating that the file or file system may be deleted, and the call should not
     *     fail if it is.
     * @return the file metadata of specific system file id
     */
    public static @NonNull File verifyNotSystemFile(
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final WritableFileStore fileStore,
            @NonNull final FileID fileId,
            final boolean canBeDeleted) {

        if (fileId.fileNum() <= ledgerConfig.numReservedSystemEntities()) {
            throw new HandleException(INVALID_FILE_ID);
        }

        var optionalFile = fileStore.get(fileId);

        if (optionalFile.isEmpty()) {
            throw new HandleException(INVALID_FILE_ID);
        }

        final var file = optionalFile.get();

        if (!file.hasKeys() || file.keys().keys().isEmpty()) {
            // @todo('protobuf change needed') change to immutable file response code
            throw new HandleException(UNAUTHORIZED);
        }

        if (!canBeDeleted && file.deleted()) {
            throw new HandleException(FILE_DELETED);
        }

        return file;
    }

    /**
     * The function validates the file id and returns the file metadata for system files. This
     * version of the method requires that the file exist in state and not be deleted. If the file
     * is empty this call will fail with {@link ResponseCodeEnum#INVALID_FILE_ID}. If the special
     * file id is greater than special file config , this call will fail with {@link
     * ResponseCodeEnum#INVALID_FILE_ID}. If the file has no keys or empty list, this call will fail
     * with {@link ResponseCodeEnum#UNAUTHORIZED}. If the file is already deleted, this call will
     * fail with {@link ResponseCodeEnum#FILE_DELETED}.
     *
     * @param ledgerConfig the ledger configuration params that is need in order to verify the max file system number.
     * @param fileStore the file store to fetch the metadata of specified file id
     * @param fileId the file id to validate and to fetch the metadata
     * @return the file metadata of specific system file id
     */
    public static @NonNull File verifyNotSystemFile(
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final WritableFileStore fileStore,
            @NonNull final FileID fileId) {
        return verifyNotSystemFile(ledgerConfig, fileStore, fileId, false);
    }

    /**
     * Verify the keys from transaction and file are relevant for the transaction
     * @param file that will be checked for verification keys
     * @param transactionKeyList transaction keys that add to context for verification.
     * @param context the handle context for the transaction.
     * @throws HandleException throws exception if {@link ResponseCodeEnum#UNAUTHORIZED}
     */
    public static void validateSignatures(
            @Nullable File file, @Nullable KeyList transactionKeyList, @NonNull HandleContext context)
            throws HandleException {

        if (transactionKeyList != null && transactionKeyList.hasKeys()) {
            for (final Key key : transactionKeyList.keys()) {
                SignatureVerification verificationResult = context.verificationFor(key);
                if (verificationResult.failed()) throw new HandleException(UNAUTHORIZED);
            }
        }

        if (file != null && file.keys() != null) {
            List<Key> fileKeyList = file.keys().keys();
            if (fileKeyList != null) {
                for (final Key key : fileKeyList) {
                    SignatureVerification verificationResult = context.verificationFor(key);
                    if (verificationResult.failed()) throw new HandleException(UNAUTHORIZED);
                }
            }
        }
    }
}
