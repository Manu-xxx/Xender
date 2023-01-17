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
package com.hedera.node.app.hapi.utils.exports;

import static com.hedera.services.stream.proto.SignatureType.SHA_384_WITH_RSA;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.StreamType;
import com.swirlds.common.stream.internal.StreamTypeFromJson;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * This is a standalone utility tool to generate signature files for event/record stream, and
 * account balance files generated by stream server.
 *
 * <p>It can also be used to sign any single files with any extension. For files except .evts and
 * .rcd, the Hash in the signature file is a SHA384 hash of all bytes in the file to be signed.
 *
 * <p>For .evts files, it generates version 5 signature files.
 *
 * <p>For .rcd files, it generates version 6 signature files.
 *
 * <p>Please see README.md for format details
 */
public class FileSignTool {
    private static final String CSV_EXTENSION = ".csv";
    private static final String ACCOUNT_BALANCE_EXTENSION = ".pb";
    private static final String SIG_FILE_NAME_END = "_sig";
    /** next bytes are signature */
    private static final byte TYPE_SIGNATURE = 3;
    /** next 48 bytes are hash384 of content of the file to be signed */
    private static final byte TYPE_FILE_HASH = 4;
    private static final int DEFAULT_RECORD_STREAM_VERSION = 6,

    private static final String STREAM_TYPE_JSON_PROPERTY = "streamTypeJson";
    private static final String LOG_CONFIG_PROPERTY = "logConfig";
    private static final String FILE_NAME_PROPERTY = "fileName";
    private static final String KEY_PROPERTY = "key";
    private static final String DEST_DIR_PROPERTY = "destDir";
    private static final String ALIAS_PROPERTY = "alias";
    private static final String PASSWORD_PROPERTY = "password";
    private static final String DIR_PROPERTY = "dir";
    private static final String APP_VERSION = "appVersion";

    private static final Logger LOGGER = LogManager.getLogger(FileSignTool.class);
    private static final Marker MARKER = MarkerManager.getMarker("FILE_SIGN");
    private static final int BYTES_COUNT_IN_INT = 4;
    /** default log4j2 file name */
    private static final String DEFAULT_LOG_CONFIG = "log4j2.xml";
    /** supported stream version file */
    /** type of the keyStore */
    private static final String KEYSTORE_TYPE = "pkcs12";
    /** name of RecordStreamType */
    private static final String RECORD_STREAM_EXTENSION = "rcd";

    private static final DigestType currentDigestType = Cryptography.DEFAULT_DIGEST_TYPE;

    /**
     * a messageDigest object for digesting entire stream file and generating entire record stream
     * file hash
     */
    private static MessageDigest streamDigest;

    /**
     * a messageDigest object for digesting metaData in the stream file and generating metaData
     * hash. Metadata contains: record stream version || HAPI proto version || startRunningHash ||
     * endRunningHash || blockNumber, where || denotes concatenation
     */
    private static MessageDigest metadataStreamDigest;

    /**
     * Digitally sign the data with the private key. Return null if anything goes wrong (e.g., bad
     * private key).
     *
     * <p>The returned signature will be at most SIG_SIZE_BYTES bytes, which is 104 for the CNSA
     * suite parameters.
     *
     * @param data the data to be signed
     * @param sigKeyPair the keyPair used for signing
     * @return the signature
     * @throws NoSuchAlgorithmException if an implementation of the required algorithm cannot be
     *     located or loaded
     * @throws NoSuchProviderException thrown if the specified provider is not registered in the
     *     security provider list
     * @throws InvalidKeyException thrown if the key is invalid
     * @throws SignatureException thrown if this signature object is not initialized properly
     */
    public static byte[] sign(final byte[] data, final KeyPair sigKeyPair)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException,
                    SignatureException {
        final Signature signature;
        signature =
                Signature.getInstance(
                        SignatureType.RSA.signingAlgorithm(), SignatureType.RSA.provider());
        signature.initSign(sigKeyPair.getPrivate());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    MARKER,
                    "data is being signed, publicKey={}",
                    hex(sigKeyPair.getPublic().getEncoded()));
        }

        signature.update(data);
        return signature.sign();
    }

    /**
     * Loads a pfx key file and return a KeyPair object
     *
     * @param keyFileName a pfx key file
     * @param password password
     * @param alias alias of the key
     * @return a KeyPair
     */
    public static KeyPair loadPfxKey(
            final String keyFileName, final String password, final String alias) {
        KeyPair sigKeyPair = null;
        try (final FileInputStream fis = new FileInputStream(keyFileName)) {
            final KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(fis, password.toCharArray());

            sigKeyPair =
                    new KeyPair(
                            keyStore.getCertificate(alias).getPublicKey(),
                            (PrivateKey) keyStore.getKey(alias, password.toCharArray()));
            LOGGER.info(MARKER, "keypair has loaded successfully from file {}", keyFileName);
        } catch (final NoSuchAlgorithmException
                | KeyStoreException
                | UnrecoverableKeyException
                | IOException
                | CertificateException e) {
            LOGGER.error(MARKER, "loadPfxKey :: ERROR ", e);
        }
        return sigKeyPair;
    }

    /**
     * builds a signature file path from a destination directory and stream file name
     *
     * @param destDir the directory to which the signature file is saved
     * @param streamFile stream file to be signed
     * @return signature file path
     */
    public static String buildDestSigFilePath(final File destDir, final File streamFile) {
        final String sigFileName = streamFile.getName() + SIG_FILE_NAME_END;
        return new File(destDir, sigFileName).getPath();
    }

    /**
     * generates signature file for the given stream file with the given KeyPair for event stream /
     * record stream v5 file, generates v5 signature file which contains a EntireHash, a
     * EntireSignature, a MetaHash, and a MetaSignature for other files, generate old signature file
     *
     * @param sigKeyPair the keyPair used for signing
     * @param streamFile the stream file to be signed
     * @param destDir the directory to which the signature file will be saved
     * @param streamType type of the stream file
     */
    public static void signSingleFile(
            final KeyPair sigKeyPair,
            final File streamFile,
            final File destDir,
            final StreamType streamType) {
        final String destSigFilePath = buildDestSigFilePath(destDir, streamFile);
        try {
            if (streamType.getExtension().equalsIgnoreCase(RECORD_STREAM_EXTENSION)) {
                createSignatureFileForRecordFile(
                        streamFile.getAbsolutePath(), streamType, sigKeyPair, destDir.getPath());
                return;
            }
        } catch (final NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidKeyException
                | SignatureException e) {
            LOGGER.error(MARKER, "Failed to sign file {} ", streamFile.getName(), e);
        }
        LOGGER.info(MARKER, "Finish generating signature file {}", destSigFilePath);
    }

    /**
     * Loads a StreamTypeFromJson object from a json file
     *
     * @param jsonPath path of the json file
     * @return a StreamType object
     * @throws IOException thrown if there are any problems during the operation
     */
    public static StreamType loadStreamTypeFromJson(final String jsonPath) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();

        final File file = new File(jsonPath);

        return objectMapper.readValue(file, StreamTypeFromJson.class);
    }

    public static void prepare(final StreamType streamType) throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");

        if (streamType.getExtension().equalsIgnoreCase(RECORD_STREAM_EXTENSION)) {
            LOGGER.info(MARKER, "registering Constructables for parsing record stream files");
            // if we are parsing new record stream files,
            // we need to add HederaNode.jar and hedera-protobuf-java-*.jar into class path,
            // so that we can register for parsing RecordStreamObject
            registry.registerConstructables("com.hedera.services.stream");
        }
    }

    private static Pair<Integer, Optional<RecordStreamFile>> readUncompressedRecordStreamFile(
            final String fileLoc) throws IOException {
        try (final FileInputStream fin = new FileInputStream(fileLoc)) {
            final int recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
            final RecordStreamFile recordStreamFile = RecordStreamFile.parseFrom(fin);
            return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
        }
    }

    private static ByteString wrapUnsafely(@NonNull final byte[] bytes) {
        return UnsafeByteOperations.unsafeWrap(bytes);
    }

    private static SignatureObject generateSignatureObject(
            final String relatedRecordStreamFile, final byte[] hash, final KeyPair sigKeyPair)
            throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException,
                    InvalidKeyException {
        final byte[] signature = sign(hash, sigKeyPair);
        return SignatureObject.newBuilder()
                .setType(SHA_384_WITH_RSA)
                .setLength(signature.length)
                .setChecksum(
                        101 - signature.length) // simple checksum to detect if at wrong place in
                // the stream
                .setSignature(wrapUnsafely(signature))
                .setHashObject(toProto(hash))
                .build();
    }

    private static HashObject toProto(final byte[] hash) {
        return HashObject.newBuilder()
                .setAlgorithm(HashAlgorithm.SHA_384)
                .setLength(currentDigestType.digestLength())
                .setHash(wrapUnsafely(hash))
                .build();
    }

    private static void createSignatureFileForRecordFile(
            final String recordFile,
            final StreamType streamType,
            final KeyPair sigKeyPair,
            final String destSigFilePath)
            throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException,
                    InvalidKeyException {

        int[] fileHeader = streamType.getFileHeader();

        // extract latest app version from system property if available
        final String appVersionString = System.getProperty(APP_VERSION);
        if (appVersionString != null) {
            final String[] versions = appVersionString.replace("-SNAPSHOT", "").split(".");
            if (versions.length >= 3) {
                try {
                    fileHeader = {
                            DEFAULT_RECORD_STREAM_VERSION,
                            Integer.parseInt(versions[0]),
                            Integer.parseInt(versions[1]),
                            Integer.parseInt(versions[2]),
                    }
                } catch (NumberFormatException e) {
                    LOGGER.error(MARKER, "Error when parsing app version string {}", appVersionString, e);
                }
            }
        }
        LOGGER.info(MARKER, "file header is {}", Arrays.toString(fileHeader));
        
        try (final SerializableDataOutputStream dosMeta =
                        new SerializableDataOutputStream(
                                new HashingOutputStream(metadataStreamDigest));
                final SerializableDataOutputStream dos =
                        new SerializableDataOutputStream(
                                new BufferedOutputStream(new HashingOutputStream(streamDigest)))) {
            // parse record file
            final Pair<Integer, Optional<RecordStreamFile>> recordResult =
                    readUncompressedRecordStreamFile(recordFile);
            final long blockNumber = recordResult.getValue().get().getBlockNumber();
            final byte[] startRunningHash =
                    recordResult
                            .getValue()
                            .get()
                            .getStartObjectRunningHash()
                            .getHash()
                            .toByteArray();
            final byte[] endRunningHash =
                    recordResult.getValue().get().getEndObjectRunningHash().getHash().toByteArray();
            final int version = recordResult.getKey();
            final byte[] serializedBytes = recordResult.getValue().get().toByteArray();

            // update meta digest
            for (final int value : fileHeader) {
                dosMeta.writeInt(value);
            }
            dosMeta.write(startRunningHash);
            dosMeta.write(endRunningHash);
            dosMeta.writeLong(blockNumber);
            dosMeta.flush();

            // update stream digest
            dos.writeInt(version);
            dos.write(serializedBytes);
            dos.flush();

        } catch (final IOException e) {
            Thread.currentThread().interrupt();
            LOGGER.error(MARKER, "Got IOException when reading record file {}", recordFile, e);
        }

        final SignatureObject metadataSignature =
                generateSignatureObject(recordFile, metadataStreamDigest.digest(), sigKeyPair);
        final SignatureObject fileSignature =
                generateSignatureObject(recordFile, streamDigest.digest(), sigKeyPair);
        final SignatureFile.Builder signatureFile =
                SignatureFile.newBuilder()
                        .setFileSignature(fileSignature)
                        .setMetadataSignature(metadataSignature);

        // create signature file
        final String sigFilePath = recordFile + "_sig";
        try (final FileOutputStream fos =
                new FileOutputStream(
                        destSigFilePath + File.separator + (new File(sigFilePath)).getName())) {
            // version in signature files is 1 byte, compared to 4 in record files
            fos.write(streamType.getSigFileHeader()[0]);
            signatureFile.build().writeTo(fos);
            LOGGER.debug(MARKER, "Signature file saved: {}", sigFilePath);
        } catch (final IOException e) {
            LOGGER.error(MARKER, "Fail to generate signature file for {}", recordFile, e);
        }
    }

    private static void initRecordDigest() {
        try {
            streamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
            metadataStreamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
        } catch (final NoSuchAlgorithmException e) {
            LOGGER.error(MARKER, "Failed to create message digest", e);
        }
    }

    public static void main(final String[] args) {
        final String streamTypeJsonPath = System.getProperty(STREAM_TYPE_JSON_PROPERTY);
        // load StreamType from json file, if such json file doesn't exist, use EVENT as streamType
        StreamType streamType = EventStreamType.getInstance();
        if (streamTypeJsonPath != null) {
            try {
                streamType = loadStreamTypeFromJson(streamTypeJsonPath);
            } catch (final IOException e) {
                LOGGER.error(MARKER, "fail to load StreamType from {}.", streamTypeJsonPath, e);
                return;
            }
        }

        // register constructables and set settings
        try {
            prepare(streamType);
            initRecordDigest();
        } catch (final ConstructableRegistryException e) {
            LOGGER.error(MARKER, "fail to register constructables.", e);
            return;
        }

        final String logConfigPath = System.getProperty(LOG_CONFIG_PROPERTY);
        final File logConfigFile =
                logConfigPath == null
                        ? getAbsolutePath().resolve(DEFAULT_LOG_CONFIG).toFile()
                        : new File(logConfigPath);
        if (logConfigFile.exists()) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.setConfigLocation(logConfigFile.toURI());

            final String fileName = System.getProperty(FILE_NAME_PROPERTY);
            final String keyFileName = System.getProperty(KEY_PROPERTY);
            final String destDirName = System.getProperty(DEST_DIR_PROPERTY);
            final String alias = System.getProperty(ALIAS_PROPERTY);
            final String password = System.getProperty(PASSWORD_PROPERTY);

            final KeyPair sigKeyPair = loadPfxKey(keyFileName, password, alias);

            final String fileDirName = System.getProperty(DIR_PROPERTY);

            try {
                // create directory if necessary
                final File destDir =
                        new File(Files.createDirectories(Paths.get(destDirName)).toUri());

                if (fileDirName != null) {
                    signAllFiles(fileDirName, destDirName, streamType, sigKeyPair);
                } else {
                    signSingleFile(sigKeyPair, new File(fileName), destDir, streamType);
                }
            } catch (final IOException e) {
                LOGGER.error(MARKER, "Got IOException", e);
            }
        }
    }

    /**
     * Sign all files in the provided directory
     *
     * @param sourceDir the directory where the files to sign are located
     * @param destDir the directory to which the signature files should be written
     * @param streamType the type of file being signed
     * @param sigKeyPair the signing key pair
     */
    public static void signAllFiles(
            final String sourceDir,
            final String destDir,
            final StreamType streamType,
            final KeyPair sigKeyPair)
            throws IOException {
        // create directory if necessary
        final File destDirFile = new File(Files.createDirectories(Paths.get(destDir)).toUri());

        final File folder = new File(sourceDir);
        final File[] streamFiles = folder.listFiles((dir, name) -> streamType.isStreamFile(name));
        final File[] accountBalanceFiles =
                folder.listFiles(
                        (dir, name) -> {
                            final String lowerCaseName = name.toLowerCase();
                            return lowerCaseName.endsWith(CSV_EXTENSION)
                                    || lowerCaseName.endsWith(ACCOUNT_BALANCE_EXTENSION);
                        });
        Arrays.sort(streamFiles); // sort by file names and timestamps
        Arrays.sort(accountBalanceFiles);

        final List<File> totalList = new ArrayList<>();
        totalList.addAll(Arrays.asList(Optional.ofNullable(streamFiles).orElse(new File[0])));
        totalList.addAll(
                Arrays.asList(Optional.ofNullable(accountBalanceFiles).orElse(new File[0])));
        for (final File item : totalList) {
            signSingleFile(sigKeyPair, item, destDirFile, streamType);
        }
    }
}
