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

package com.hedera.node.app.records.files;

import static com.hedera.node.app.records.files.StreamFileProducerBase.COMPRESSION_ALGORITHM_EXTENSION;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.*;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple stateless class with static methods to write signature files. It cleanly separates out the code for generating signature files.
 */
public final class SignatureFileWriter {
    /** Logger to use */
    private static final Logger log = LogManager.getLogger(SignatureFileWriter.class);
    /** The suffix added to RECORD_EXTENSION for the record signature files */
    private static final String RECORD_SIG_EXTENSION_SUFFIX = "_sig";

    /**
     * Write a signature file for a record file
     *
     * @param recordFilePath the path to the record file
     * @param recordFileHash the hash of the record file
     * @param signer the signer
     * @param signatureFileVersion the signature file version
     * @param writeMetadataSignature whether to write the metadata signature
     * @param recordFileVersion the record file version
     * @param hapiProtoVersion the hapi proto version
     * @param blockNumber the block number
     * @param startRunningHash the start running hash
     * @param endRunningHash the end running hash
     */
    public static void writeSignatureFile(
            @NonNull final Path recordFilePath,
            @NonNull Bytes recordFileHash,
            @NonNull final Signer signer,
            final int signatureFileVersion,
            final boolean writeMetadataSignature,
            final int recordFileVersion,
            final SemanticVersion hapiProtoVersion,
            final long blockNumber,
            @NonNull final Bytes startRunningHash,
            @NonNull final Bytes endRunningHash) {
        // write signature file
        final var sigFilePath = getSigFilePath(recordFilePath);
        try (final var fileOut = Files.newOutputStream(sigFilePath, StandardOpenOption.CREATE_NEW)) {
            final var streamingData = new WritableStreamingData(fileOut);
            Bytes metadataHash = null;
            if (writeMetadataSignature) {
                // create metadata hash
                HashingOutputStream hashingOutputStream =
                        new HashingOutputStream(MessageDigest.getInstance(DigestType.SHA_384.algorithmName()));
                SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(hashingOutputStream);
                dataOutputStream.writeInt(recordFileVersion);
                dataOutputStream.writeInt(hapiProtoVersion.major());
                dataOutputStream.writeInt(hapiProtoVersion.minor());
                dataOutputStream.writeInt(hapiProtoVersion.patch());
                startRunningHash.writeTo(dataOutputStream);
                endRunningHash.writeTo(dataOutputStream);
                dataOutputStream.writeLong(blockNumber);
                dataOutputStream.close();
                metadataHash = Bytes.wrap(hashingOutputStream.getDigest());
            }
            // create signature file
            final var signatureFile = new SignatureFile(
                    generateSignatureObject(signer, recordFileHash),
                    writeMetadataSignature ? generateSignatureObject(signer, metadataHash) : null);
            // write version in signature file. It is only 1 byte, compared to 4 in record files
            streamingData.writeByte((byte) signatureFileVersion);
            // write protobuf SignatureFile
            SignatureFile.PROTOBUF.write(signatureFile, streamingData);
            log.debug("closeCurrentAndSign :: signature file saved: {}", sigFilePath);
            // flush
            fileOut.flush();
        } catch (final IOException e) {
            log.error("closeCurrentAndSign ::  :: Fail to generate signature file for {}", recordFilePath, e);
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given a hash produce the HAPI signature object for that hash
     *
     * @param signer The signer to use
     * @param hash The hash to sign
     * @return Signature object
     */
    private static SignatureObject generateSignatureObject(@NonNull final Signer signer, @NonNull final Bytes hash) {
        final Bytes signature = Bytes.wrap(signer.sign(hash.toByteArray()).getSignatureBytes());
        return SignatureObject.newBuilder()
                .type(SignatureType.SHA_384_WITH_RSA)
                .length((int) signature.length())
                .checksum(101 - (int) signature.length()) // simple checksum to detect if at wrong place in
                .signature(signature)
                .hashObject(new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash))
                .build();
    }

    /**
     * Get the signature file path for a record file
     *
     * @param recordFilePath the path to the record file
     * @return the path to the signature file
     */
    static Path getSigFilePath(@NonNull final Path recordFilePath) {
        String recordFileName = recordFilePath.getFileName().toString();
        if (recordFileName.endsWith(COMPRESSION_ALGORITHM_EXTENSION)) {
            recordFileName =
                    recordFileName.substring(0, recordFileName.length() - COMPRESSION_ALGORITHM_EXTENSION.length());
        }
        return recordFilePath.resolveSibling(recordFileName + RECORD_SIG_EXTENSION_SUFFIX);
    }
}
