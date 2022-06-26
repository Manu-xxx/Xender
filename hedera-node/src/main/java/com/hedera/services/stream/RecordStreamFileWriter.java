package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.CodedOutputStream;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.LinkedObjectStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateSigFilePath;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static com.swirlds.common.stream.StreamAligned.NO_ALIGNMENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM_FILE;

class RecordStreamFileWriter implements LinkedObjectStream<RecordStreamObject> {
	private static final Logger LOG = LogManager.getLogger(RecordStreamFileWriter.class);

	private static final DigestType currentDigestType = DigestType.SHA_384;

	/**
	 * <	 * the current record stream type;
	 * used to obtain file extensions and versioning
	 */
	private final RecordStreamType streamType;

	/**
	 * a messageDigest object for digesting entire stream file and generating entire record stream file hash
	 */
	private final MessageDigest streamDigest;

	/**
	 * a messageDigest object for digesting metaData in the stream file and generating metaData hash.
	 * Metadata contains:
	 * record stream version || HAPI proto version || startRunningHash || endRunningHash || blockNumber, where
	 * || denotes concatenation
	 */
	private final MessageDigest metadataStreamDigest;

	/**
	 * Output stream for digesting metaData. Metadata should be written to this stream. Any data written to this
	 * stream is used to generate a running metadata hash.
	 */
	private SerializableDataOutputStream dosMeta = null;

	/**
	 * current runningHash before consuming the object added by calling {@link #addObject(RecordStreamObject)} method
	 */
	private RunningHash runningHash;

	/**
	 * signer for generating signatures
	 */
	private final Signer signer;

	/**
	 * The desired amount of time that data should be written into a file before starting a new file.
	 */
	private final long logPeriodMs;

	/**
	 * The previous object that was passed to the stream.
	 */
	private RecordStreamObject previousObject;

	/**
	 * Tracks if the previous object was held back in the previous file due to its alignment.
	 */
	private boolean previousHeldBackByAlignment;

	/**
	 * if it is true, we don't write object stream file until the first complete window. This is suitable for streaming
	 * after reconnect, so that reconnect node can generate the same stream files as other nodes after reconnect.
	 *
	 * if it is false, we start to write object stream file immediately. This is suitable for streaming after
	 * restart, so that no object would be missing in the nodes' stream files
	 */
	private boolean startWriteAtCompleteWindow;

	/**
	 * the path to which we write record stream files and signature files
	 */
	private final String dirPath;
	private RecordStreamFile.Builder recordStreamFileBuilder;
	private int recordFileVersion;

	public RecordStreamFileWriter(
			final String dirPath,
			final long logPeriodMs,
			final Signer signer,
			final boolean startWriteAtCompleteWindow,
			final RecordStreamType streamType
	) throws NoSuchAlgorithmException {
		this.dirPath = dirPath;
		this.logPeriodMs = logPeriodMs;
		this.signer = signer;
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		this.streamType = streamType;
		this.streamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
		this.metadataStreamDigest = MessageDigest.getInstance(currentDigestType.algorithmName());
	}

	@Override
	public void addObject(final RecordStreamObject object) {
		if (shouldStartNewFile(object)) {
			// if we are currently writing a file,
			// finish current file and generate signature file
			closeCurrentAndSign();
			// write the beginning of the new file
			beginNew(object);
		}

		// if recordStreamFile is null, it means startWriteAtCompleteWindow is true,
		// and we are still in the first incomplete window, so we don't serialize this object;
		// so we only serialize the object when stream is not null
		if (recordStreamFileBuilder != null) {
			consume(object);
		}

		// update runningHash
		this.runningHash = object.getRunningHash();
	}

	/**
	 * <p>
	 * Check whether the provided object needs to be written into a new file, or if it should be written
	 * into the current file.
	 * </p>
	 *
	 * <p>
	 * Time is divided into windows determined by provided configuration. An object is chosen to start a new file
	 * if it is the first encountered object with a timestamp in the next window -- as long as the object has
	 * a different stream alignment than the previous object. If an object has a matching stream alignment as the
	 * previous object then it is always placed in the same file as the previous object. Alignment is ignored for
	 * objects with {@link StreamAligned#NO_ALIGNMENT}.
	 * </p>
	 *
	 * @param nextObject
	 * 		the object currently being added to the stream
	 * @return whether the object should be written into a new file
	 */
	private boolean shouldStartNewFile(final RecordStreamObject nextObject) {
		try {
			if (previousObject == null) {
				// This is the first object. It may be the first thing in a file, but it is impossible
				// to make that determination at this point in time.
				return !startWriteAtCompleteWindow;
			} else {
				// Check if this object is in a different period than the previous object.
				final long previousPeriod = getPeriod(previousObject.getTimestamp(), logPeriodMs);
				final long currentPeriod = getPeriod(nextObject.getTimestamp(), logPeriodMs);
				final boolean differentPeriod = previousPeriod != currentPeriod;

				// Check if this object has a different alignment than the previous object. Objects with NO_ALIGNMENT
				// are always considered to be unaligned with any other object.
				final boolean differentAlignment =
						previousObject.getStreamAlignment() != nextObject.getStreamAlignment() ||
								nextObject.getStreamAlignment() == NO_ALIGNMENT;

				// If this object is in a new period with respect to the current file, and no
				// objects have yet been written to the next file.
				final boolean timestampIsEligibleForNextFile = previousHeldBackByAlignment || differentPeriod;

				if (timestampIsEligibleForNextFile && !differentAlignment) {
					// This object has the same alignment as the one that came before it, so we must hold it back.
					previousHeldBackByAlignment = true;
					return false;
				}

				previousHeldBackByAlignment = false;
				return timestampIsEligibleForNextFile;
			}
		} finally {
			previousObject = nextObject;
		}
	}

	/**
	 * if recordStreamFile is not null:
	 * write last runningHash to current file;
	 * close current file;
	 * and generate a corresponding signature file
	 */
	public void closeCurrentAndSign() {
		if (recordStreamFileBuilder != null) {
			// generate file name
			final var firstTxnTimestamp =
					recordStreamFileBuilder.getRecordStreamItems(0).getRecord().getConsensusTimestamp();
			final var file = new File(
					generateStreamFilePath(
							Instant.ofEpochSecond(firstTxnTimestamp.getSeconds(), firstTxnTimestamp.getNanos())));
			final var fileNameShort = file.getName(); // for logging purposes
			if (file.exists() && !file.isDirectory()) {
				LOG.debug(OBJECT_STREAM.getMarker(), "Stream file already exists {}", fileNameShort);
			} else {
				try {
					// write endRunningHash
					final var endRunningHash = runningHash.getFutureHash().get();
					recordStreamFileBuilder.setEndObjectRunningHash(toProto(endRunningHash.getValue()));
					dosMeta.write(endRunningHash.getValue());
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write endRunningHash {}",
							endRunningHash);

					// write block number to metadata
					dosMeta.writeLong(recordStreamFileBuilder.getBlockNumber());
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write block number {}",
							recordStreamFileBuilder.getBlockNumber());
				} catch (InterruptedException | ExecutionException e) {
					Thread.currentThread().interrupt();
					LOG.error(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: failed when getting endRunningHash for writing {}",
							fileNameShort, e);
					return;
				} catch (IOException e) {
					Thread.currentThread().interrupt();
					LOG.warn(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: IOException when serializing endRunningHash and block number into "
									+ "metadata", e);
					return;
				}

				/* When sidecars are enabled we will need to add more logic at this point:
					1. We should have a map (?) of the TransactionSidecarRecords with key - the type of sidecar, updated
					everytime we consume a RSO.
					2. At this point, we will create a SidecarFile for each present type of sidecar record in the
					current
					 block
					3. We populate the SidecarMetadata of the RecordStreamFile message
				*/

				// create record file
				try (FileOutputStream stream = new FileOutputStream(file, false);
					 SerializableDataOutputStream dos = new SerializableDataOutputStream(
							 new BufferedOutputStream(new HashingOutputStream(streamDigest, stream)))
				) {
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Stream file created {}", fileNameShort);

					// write contents of record file - record file version and serialized RecordFile protobuf
					dos.writeInt(recordFileVersion);
					dos.write(serialize(recordStreamFileBuilder));

					// make sure the whole file is written to disk
					dos.flush();
					stream.flush();
					stream.getChannel().force(true);
					stream.getFD().sync();
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Stream file written successfully {}", fileNameShort);

					// close dosMeta manually; stream and dos will be automatically closed
					dosMeta.close();
					dosMeta = null;
					recordStreamFileBuilder = null;

					LOG.debug(OBJECT_STREAM_FILE.getMarker(),
							"File {} is closed at {}", () -> fileNameShort, Instant::now);
				} catch (FileNotFoundException e) {
					Thread.currentThread().interrupt();
					LOG.error(EXCEPTION.getMarker(), "closeCurrentAndSign :: FileNotFound: {}", e.getMessage());
					return;
				} catch (IOException e) {
					Thread.currentThread().interrupt();
					LOG.warn(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: IOException when serializing {}", recordStreamFileBuilder, e);
					return;
				}

				// if this line is reached, record file has been created successfully, so create its signature
				createSignatureFile(file);
			}
		}
	}

	/**
	 * write the beginning part of the record stream file and metadata:
	 * record stream version, HAPI proto version and initial runningHash.
	 */
	private void beginNew(final RecordStreamObject object) {
		final var fileHeader = streamType.getFileHeader();
		// instead of creating the record file here and writing
		// the record file version in it, save the version and
		// perform the whole file creation in {@link #closeCurrentAndSign()} method
		recordFileVersion = fileHeader[0];
		// add known values to recordStreamFile proto
		recordStreamFileBuilder = RecordStreamFile.newBuilder().setBlockNumber(object.getStreamAlignment());
		recordStreamFileBuilder.setHapiProtoVersion(SemanticVersion.newBuilder()
				.setMajor(fileHeader[1])
				.setMinor(fileHeader[2])
				.setPatch(fileHeader[3])
		);
		dosMeta = new SerializableDataOutputStream(new HashingOutputStream(metadataStreamDigest));
		try {
			// write record stream version and HAPI version to metadata
			for (final var value : fileHeader) {
				dosMeta.writeInt(value);
			}
			// write startRunningHash
			final var startRunningHash = runningHash.getFutureHash().get();
			recordStreamFileBuilder.setStartObjectRunningHash(toProto(startRunningHash.getValue()));
			dosMeta.write(startRunningHash.getValue());
			LOG.debug(
					OBJECT_STREAM_FILE.getMarker(),
					"beginNew :: write startRunningHash to metadata {}", startRunningHash);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
			LOG.error(
					EXCEPTION.getMarker(),
					"beginNew :: Got IOException when writing startRunningHash to metadata stream", e);
		} catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			LOG.error(
					EXCEPTION.getMarker(),
					"beginNew :: Exception when getting startRunningHash for writing to metadata stream", e);
		}
	}

	/**
	 * add given object to the current record stream file
	 *
	 * @param object
	 * 		object to be added to the record stream file
	 */
	private void consume(final RecordStreamObject object) {
		recordStreamFileBuilder.addRecordStreamItems(
				RecordStreamItem.newBuilder()
						.setTransaction(object.getTransaction())
						.setRecord(object.getTransactionRecord())
						.build()
		);

		// In phase 2 we will need to add logic here to also
		// save the sidecar records of the current object
	}

	/**
	 * generate full fileName from given Instant object
	 *
	 * @param consensusTimestamp
	 * 		the consensus timestamp of the first transaction in the record file
	 * @return the new record file name
	 */
	String generateStreamFilePath(final Instant consensusTimestamp) {
		return dirPath + File.separator + generateStreamFileNameFromInstant(consensusTimestamp, streamType);
	}

	public void setRunningHash(final Hash hash) {
		this.runningHash = new RunningHash(hash);
	}

	/**
	 * this method is called when the node falls behind
	 * resets all populated up to this point fields (metadataDigest, dosMeta, recordStreamFile)
	 */
	@Override
	public void clear() {
		if (dosMeta != null) {
			try {
				dosMeta.close();
				metadataStreamDigest.reset();
				dosMeta = null;
			} catch (IOException e) {
				LOG.warn(EXCEPTION.getMarker(), "RecordStreamFileWriter::clear Exception in closing dosMeta", e);
			}
		}
		recordStreamFileBuilder = null;
		LOG.debug(OBJECT_STREAM.getMarker(), "RecordStreamFileWriter::clear executed.");
	}

	public void close() {
		this.closeCurrentAndSign();
		LOG.debug(LogMarker.FREEZE.getMarker(), "RecordStreamFileWriter finished writing the last object, is stopped");
	}

	public void setStartWriteAtCompleteWindow(final boolean startWriteAtCompleteWindow) {
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		LOG.debug(OBJECT_STREAM.getMarker(),
				"RecordStreamFileWriter::setStartWriteAtCompleteWindow: {}", startWriteAtCompleteWindow);
	}

	public boolean getStartWriteAtCompleteWindow() {
		return this.startWriteAtCompleteWindow;
	}

	/**
	 * Helper method that serializes a RecordStreamFile.
	 * Uses deterministic serialization to ensure multiple invocations on the same
	 * object lead to identical serialization.
	 *
	 * @param recordStreamFileProtoBuilder
	 * 		the RecordStreamFile object that needs to be serialized
	 * @return the serialized bytes
	 */
	private byte[] serialize(final RecordStreamFile.Builder recordStreamFileProtoBuilder) throws IOException {
		final var recordStreamFileProto = recordStreamFileProtoBuilder.build();
		final var result = new byte[recordStreamFileProto.getSerializedSize()];
		final var output = CodedOutputStream.newInstance(result);
		output.useDeterministicSerialization();
		recordStreamFileProto.writeTo(output);
		output.checkNoSpaceLeft();
		return result;
	}

	private HashObject toProto(final byte[] hash) {
		return HashObject.newBuilder()
				.setAlgorithm(HashAlgorithm.SHA_384)
				.setLength(currentDigestType.digestLength())
				.setHash(ByteStringUtils.wrapUnsafely(hash))
				.build();
	}

	private void createSignatureFile(final File relatedRecordStreamFile) {
		// create proto messages for signature file
		final var fileSignature = generateSignatureObject(streamDigest.digest());
		final var metadataSignature = generateSignatureObject(metadataStreamDigest.digest());
		final var signatureFile = SignatureFile.newBuilder()
				.setFileSignature(fileSignature)
				.setMetadataSignature(metadataSignature);

		// create signature file
		final var sigFilePath = generateSigFilePath(relatedRecordStreamFile);
		try (final var fos = new FileOutputStream(sigFilePath)) {
			// version in signature files is 1 byte, compared to 4 in record files
			fos.write(streamType.getSigFileHeader()[0]);
			signatureFile.build().writeTo(fos);
			LOG.debug(OBJECT_STREAM_FILE.getMarker(),
					"closeCurrentAndSign :: signature file saved: {}", sigFilePath);
		} catch (IOException e) {
			LOG.error(EXCEPTION.getMarker(),
					"closeCurrentAndSign ::  :: Fail to generate signature file for {}",
					relatedRecordStreamFile.getName(), e);
		}
	}

	private SignatureObject generateSignatureObject(final byte[] hash) {
		final var signature = signer.sign(hash);
		return SignatureObject.newBuilder()
				.setType(SignatureType.SHA_384_WITH_RSA)
				.setLength(signature.length)
				.setChecksum(101 - signature.length) // simple checksum to detect if at wrong place in the stream
				.setSignature(ByteStringUtils.wrapUnsafely(signature))
				.setHashObject(toProto(hash))
				.build();
	}

	@VisibleForTesting
	void clearRunningHash() {
		runningHash = new RunningHash();
	}
}
