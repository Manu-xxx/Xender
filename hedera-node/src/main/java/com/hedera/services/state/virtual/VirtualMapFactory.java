package com.hedera.services.state.virtual;

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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.io.File;
import java.nio.file.Paths;

public class VirtualMapFactory {
	private static final short CURRENT_SERIALIZATION_VERSION = 1;

	private static final long MAX_BLOBS = 50_000_000;
	private static final long MAX_STORAGE_ENTRIES = 500_000_000;
	private static final long MAX_IN_MEMORY_INTERNAL_HASHES = 0;

	private static final String BLOBS_VM_NAME = "blobs";
	private static final String STORAGE_VM_NAME = "storage";

	@FunctionalInterface
	public interface JasperDbBuilderFactory {
		<K extends VirtualKey, V extends VirtualValue> JasperDbBuilder<K, V> newJdbBuilder();
	}

	private final String jdbDataLoc;
	private final JasperDbBuilderFactory jdbBuilderFactory;

	public VirtualMapFactory(
			final String jdbDataLoc,
			final JasperDbBuilderFactory jdbBuilderFactory
	) {
		this.jdbDataLoc = jdbDataLoc;
		this.jdbBuilderFactory = jdbBuilderFactory;
	}

	public VirtualMap<VirtualBlobKey, VirtualBlobValue> newVirtualizedBlobs() {
		final var blobKeySerializer = new VirtualBlobKeySerializer();
		final VirtualLeafRecordSerializer<VirtualBlobKey, VirtualBlobValue> blobLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						CURRENT_SERIALIZATION_VERSION,
						DigestType.SHA_384,
						CURRENT_SERIALIZATION_VERSION,
						VirtualBlobKey.sizeInBytes(),
						new VirtualBlobKeySupplier(),
						CURRENT_SERIALIZATION_VERSION,
						VirtualBlobValue.sizeInBytes(),
						new VirtualBlobValueSupplier(),
						false);

		final JasperDbBuilder<VirtualBlobKey, VirtualBlobValue> dsBuilder = jdbBuilderFactory.newJdbBuilder();
		dsBuilder
				.virtualLeafRecordSerializer(blobLeafRecordSerializer)
				.virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
				.keySerializer(blobKeySerializer)
				.storageDir(Paths.get(blobsLoc()))
				.maxNumOfKeys(MAX_BLOBS)
				.preferDiskBasedIndexes(false)
				.internalHashesRamToDiskThreshold(MAX_IN_MEMORY_INTERNAL_HASHES)
				.mergingEnabled(true);
		return new VirtualMap<>(BLOBS_VM_NAME, dsBuilder);
	}

	public VirtualMap<ContractKey, ContractValue> newVirtualizedStorage() {
		final var storageKeySerializer = new ContractKeySerializer();
		final VirtualLeafRecordSerializer<ContractKey, ContractValue> storageLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						CURRENT_SERIALIZATION_VERSION,
						DigestType.SHA_384,
						CURRENT_SERIALIZATION_VERSION,
						storageKeySerializer.getSerializedSize(),
						new ContractKeySupplier(),
						CURRENT_SERIALIZATION_VERSION,
						ContractValue.SERIALIZED_SIZE,
						new ContractValueSupplier(),
						true);

		final JasperDbBuilder<ContractKey, ContractValue> dsBuilder = jdbBuilderFactory.newJdbBuilder();
		dsBuilder
				.virtualLeafRecordSerializer(storageLeafRecordSerializer)
				.virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
				.keySerializer(storageKeySerializer)
				.storageDir(Paths.get(storageLoc()))
				.maxNumOfKeys(MAX_STORAGE_ENTRIES)
				.preferDiskBasedIndexes(false)
				.internalHashesRamToDiskThreshold(MAX_IN_MEMORY_INTERNAL_HASHES)
				.mergingEnabled(true);
		return new VirtualMap<>(STORAGE_VM_NAME, dsBuilder);
	}

	private String blobsLoc() {
		return jdbDataLoc + File.separator + BLOBS_VM_NAME;
	}

	private String storageLoc() {
		return jdbDataLoc + File.separator + STORAGE_VM_NAME;
	}
}
