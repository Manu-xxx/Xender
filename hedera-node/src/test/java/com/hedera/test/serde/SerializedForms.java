package com.hedera.test.serde;

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

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.core.jproto.TxnReceiptSerdeTest;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountStateSerdeTest;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleNetworkContextSerdeTest;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleScheduleSerdeTest;
import com.hedera.services.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.services.state.merkle.MerkleScheduledTransactionsStateSerdeTest;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleStakingInfoSerdeTest;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTokenSerdeTest;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleTopicSerdeTest;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.EvmFnResultSerdeTest;
import com.hedera.services.state.submerkle.EvmLog;
import com.hedera.services.state.submerkle.EvmLogSerdeTest;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.state.submerkle.TxnIdSerdeTest;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.SeededPropertySource;
import com.hedera.test.utils.SerdeUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.utility.CommonUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.test.serde.SelfSerializableDataTest.MIN_TEST_CASES_PER_VERSION;
import static com.hedera.test.utils.SerdeUtils.serializeToHex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The purpose of this executable is to generate the latest serialized bytes for the
 * serialization-deserialization (serde) tests for serializable classes. Please DO NOT remove classes from being
 * serialized unless you are absolutely sure they are no longer in saved signed state data.
 *
 * When running this file, be sure to set the hedera-services/hedera-node directory as the working directory
 * before running.
 */
public class SerializedForms {
	private static final String SERIALIZED_FORMS_LOC = "src/test/resources/serdes";
	private static final String FORM_TPL = "%s-v%d-sn%d.hex";

	public static void main(String... args) {
		generateSerializedData();
	}

	public static <T extends SelfSerializable> byte[] loadForm(
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		final var path = pathFor(type, version, testCaseNo);
		try {
			return CommonUtils.unhex(Files.readString(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static <T extends SelfSerializable> void assertSameSerialization(
			final Class<T> type,
			final Function<SeededPropertySource, T> factory,
			final int version,
			final int testCaseNo
	) {
		final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
		final var example = factory.apply(propertySource);
		final var actual = SerdeUtils.serialize(example);
		final var expected = loadForm(type, version, testCaseNo);
		assertArrayEquals(
				expected, actual,
				"Regression in serializing test case #" + testCaseNo);
	}

	private static void generateSerializedData() {
//		GENERATOR_MAPPING.get(MerkleStakingInfo.class).run();
		GENERATOR_MAPPING.get(EvmFnResult.class).run();
//		GENERATOR_MAPPING.get(ExpirableTxnRecord.class).run();
//		for (var entry : GENERATOR_MAPPING.entrySet()) {
//			entry.getValue().run();
//		}
	}

	private static <T extends SelfSerializable> Map.Entry<Class<T>, Runnable> entry(
			Class<T> classType,
			Function<SeededPropertySource, T> factoryFn,
			int numTests) {
		return Map.entry(classType, () -> saveForCurrentVersion(classType, factoryFn, numTests));
	}

	/**
	 * The entries in this map will be used to construct serializable object classes and generate serialized bytes that
	 * can be used for testing. The entries consist of:
	 * - the serializable class type (e.g., SomeSerializableObject.class)
	 * - function that takes a SeededPropertySource instance and generates an instance of the class filled with random
	 * data
	 * - an integer specifying the number of test cases to generate.
	 */
	private static final Map<Class<? extends SelfSerializable>, Runnable> GENERATOR_MAPPING =
			Map.ofEntries(
//					entry(CurrencyAdjustments.class, SeededPropertySource::nextCurrencyAdjustments,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(EntityId.class, SeededPropertySource::nextEntityId, MIN_TEST_CASES_PER_VERSION),
					entry(EvmFnResult.class, SeededPropertySource::nextEvmResult, EvmFnResultSerdeTest.NUM_TEST_CASES),
//					entry(EvmLog.class, SeededPropertySource::nextEvmLog, EvmLogSerdeTest.NUM_TEST_CASES),
//					entry(ExchangeRates.class, SeededPropertySource::nextExchangeRates, MIN_TEST_CASES_PER_VERSION),
					entry(ExpirableTxnRecord.class, SeededPropertySource::nextRecord,
							ExpirableTxnRecordSerdeTest.NUM_TEST_CASES)
//					entry(FcAssessedCustomFee.class, SeededPropertySource::nextAssessedFee, MIN_TEST_CASES_PER_VERSION),
//					entry(FcCustomFee.class, SeededPropertySource::nextCustomFee, MIN_TEST_CASES_PER_VERSION),
//					entry(FcTokenAllowance.class, SeededPropertySource::nextFcTokenAllowance,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(FcTokenAllowanceId.class, SeededPropertySource::nextAllowanceId, MIN_TEST_CASES_PER_VERSION),
//					entry(FcTokenAssociation.class, SeededPropertySource::nextTokenAssociation,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(BytesElement.class, SeededPropertySource::nextFilePart, MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleAccountState.class, SeededPropertySource::nextAccountState,
//							MerkleAccountStateSerdeTest.NUM_TEST_CASES),
//					entry(MerkleEntityId.class, SeededPropertySource::nextMerkleEntityId, MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleNetworkContext.class, SeededPropertySource::next0270NetworkContext,
//							MerkleNetworkContextSerdeTest.NUM_TEST_CASES),
//					entry(MerkleScheduledTransactionsState.class, SeededPropertySource::nextScheduledTransactionsState,
//							MerkleScheduledTransactionsStateSerdeTest.NUM_TEST_CASES),
//					entry(MerkleSchedule.class, MerkleScheduleSerdeTest::nextSchedule,
//							MerkleScheduleSerdeTest.NUM_TEST_CASES),
//					entry(MerkleSpecialFiles.class, SeededPropertySource::nextMerkleSpecialFiles,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleToken.class, SeededPropertySource::nextToken, MerkleTokenSerdeTest.NUM_TEST_CASES),
//					entry(MerkleTokenRelStatus.class, SeededPropertySource::nextMerkleTokenRelStatus,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleTopic.class, SeededPropertySource::nextTopic, MerkleTopicSerdeTest.NUM_TEST_CASES),
//					entry(MerkleUniqueToken.class, SeededPropertySource::next0260UniqueToken,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(NftAdjustments.class, SeededPropertySource::nextOwnershipChanges, MIN_TEST_CASES_PER_VERSION),
//					entry(RecordsRunningHashLeaf.class, SeededPropertySource::nextRecordsRunningHashLeaf,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(TxnId.class, SeededPropertySource::nextTxnId, TxnIdSerdeTest.NUM_TEST_CASES),
//					entry(TxnReceipt.class, TxnReceiptSerdeTest::receiptFactory, 2 * MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleAccountTokens.class, SeededPropertySource::nextMerkleAccountTokens,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(ContractKey.class, SeededPropertySource::nextContractKey, MIN_TEST_CASES_PER_VERSION),
//					entry(ContractValue.class, SeededPropertySource::nextContractValue, MIN_TEST_CASES_PER_VERSION),
//					entry(VirtualBlobKey.class, SeededPropertySource::nextVirtualBlobKey, MIN_TEST_CASES_PER_VERSION),
//					entry(VirtualBlobValue.class, SeededPropertySource::nextVirtualBlobValue,
//							MIN_TEST_CASES_PER_VERSION),
//					entry(MerkleStakingInfo.class, SeededPropertySource::nextStakingInfo,
//							MerkleStakingInfoSerdeTest.NUM_TEST_CASES)
	);

	private static <T extends SelfSerializable> void saveForCurrentVersion(
			final Class<T> type,
			final Function<SeededPropertySource, T> factory,
			final int numTestCases
	) {
		final var instance = SelfSerializableDataTest.instantiate(type);
		final var currentVersion = instance.getVersion();
		for (int i = 0; i < numTestCases; i++) {
			final var propertySource = SeededPropertySource.forSerdeTest(currentVersion, i);
			final var example = factory.apply(propertySource);
			saveForm(example, type, currentVersion, i);
		}
	}

	private static <T extends SelfSerializable> void saveForm(
			final T example,
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		final var hexed = serializeToHex(example);
		final var path = pathFor(type, version, testCaseNo);
		try {
			Files.writeString(path, hexed);
			System.out.println("Please ensure " + path + " is tracked in git");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static <T extends SelfSerializable> Path pathFor(
			final Class<T> type,
			final int version,
			final int testCaseNo
	) {
		return Paths.get(SERIALIZED_FORMS_LOC + "/"
				+ String.format(FORM_TPL, type.getSimpleName(), version, testCaseNo));
	}
}
