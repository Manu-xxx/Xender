package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.google.protobuf.ByteOutput;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Protobuf related utilities shared by client and server.
 */
public final class ProtoCommonUtils {
	private static final Logger log = LogManager.getLogger(ProtoCommonUtils.class);

	public static ByteString wrapUnsafely(@NotNull final byte[] bytes) {
		return UnsafeByteOperations.unsafeWrap(bytes);
	}

	/**
	 * This method converts a protobuf ByteString into a byte array. Optimization is done in case the input is a
	 * LiteralByteString to not make a copy of the underlying array and return it as is. This is okay for our purposes
	 * since we never modify the array and just directly store it in the database.
	 * <p>
	 * If the ByteString is smaller than the estimated size to allocate an UnsafeByteOutput object, copy the array
	 * regardless since we'd be allocating a similar amount of memory either way.
	 *
	 * @param byteString
	 * 		to convert
	 * @return bytes extracted from the ByteString
	 */
	public static byte[] unwrapUnsafelyIfPossible(@NotNull final ByteString byteString) {
		try {
			if (UnsafeByteOutput.supports(byteString)) {
				final var byteOutput = new UnsafeByteOutput();
				UnsafeByteOperations.unsafeWriteTo(byteString, byteOutput);
				return byteOutput.bytes;
			}
		} catch (IOException e) {
			log.warn("Unsafe retrieval of bytes failed", e);
		}
		return byteString.toByteArray();
	}

	static class UnsafeByteOutput extends ByteOutput {
		// Size of the object header plus a compressed object reference to bytes field
		static final short SIZE = 12 + 4;
		static final Class<?> SUPPORTED_CLASS;

		static {
			try {
				SUPPORTED_CLASS = Class.forName(ByteString.class.getName() + "$LiteralByteString");
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		private byte[] bytes;

		@Override
		public void write(byte value) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			this.bytes = bytes;
		}

		@Override
		public void writeLazy(byte[] bytes, int offset, int length) {
			this.bytes = bytes;
		}

		@Override
		public void write(ByteBuffer value) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void writeLazy(ByteBuffer value) {
			throw new UnsupportedOperationException();
		}

		@VisibleForTesting
		byte[] getBytes() {
			return bytes;
		}

		@VisibleForTesting
		static boolean supports(final ByteString byteString) {
			return byteString.size() > UnsafeByteOutput.SIZE
					&& byteString.getClass() == UnsafeByteOutput.SUPPORTED_CLASS;
		}
	}

	private ProtoCommonUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
