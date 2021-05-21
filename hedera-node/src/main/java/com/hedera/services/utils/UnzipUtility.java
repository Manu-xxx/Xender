package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UnzipUtility {

	private static final Logger log = LogManager.getLogger(UnzipUtility.class);

	private static final int BUFFER_SIZE = 4096;

	private UnzipUtility() {
		throw new IllegalStateException("UnzipUtility is an utility class. Shouldn't create any instance!");
	}
	/**
	 * Extracts a zip entry (file entry)
	 *
	 * @param inputStream
	 * 		Input stream of zip file content
	 * @param filePath
	 * 		Output file name
	 */
	private static void extractSingleFile(ZipInputStream inputStream, String filePath) {
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
			byte[] bytesIn = new byte[BUFFER_SIZE];
			int read = 0;
			while ((read = inputStream.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
		} catch (IOException e) {
			log.error("Unable to write to file : {}", filePath);
			log.error(e.getMessage());
		}
	}

	public static void unzip(final byte[] bytes, final String dstDirectory) throws IOException {
		final File destDir = new File(dstDirectory);
		if (!destDir.exists()) {
			log.info("create dir " + destDir);
			destDir.mkdir();
		}
		final ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes));
		ZipEntry entry = zipIn.getNextEntry();
		while (entry != null) {
			String filePath = dstDirectory + File.separator + entry.getName();
			final File fileOrDir = new File(filePath);
			filePath = fileOrDir.getCanonicalPath();
			if(!filePath.startsWith(destDir.getCanonicalPath() + File.separator)) {
				throw new IllegalArgumentException("Zip file entry has invalid path " + filePath +
						". Only specify a filePath with correct canonical path name.");
			}
			if (!entry.isDirectory()) {
				log.info("create file {}", filePath);
				extractSingleFile(zipIn, filePath);
			} else {
				log.info("create sub dir " + fileOrDir);
				fileOrDir.mkdir();
			}
			zipIn.closeEntry();
			entry = zipIn.getNextEntry();
		}
		zipIn.close();
	}
}
