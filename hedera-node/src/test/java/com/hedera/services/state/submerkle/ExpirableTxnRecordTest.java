package com.hedera.services.state.submerkle;

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

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MISSING_PARENT_CONSENSUS_TIMESTAMP;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.NO_CHILD_TRANSACTIONS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.allToGrpc;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.utils.TxnUtils.withNftAdjustments;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;

class ExpirableTxnRecordTest {
	private static final long expiry = 1_234_567L;
	private static final long submittingMember = 1L;
	private static final long packedParentConsTime = packedTime(expiry, 890);
	private static final short numChildRecords = 2;

	private static final byte[] pretendHash = "not-really-a-hash".getBytes();

	private static final TokenID nft = IdUtils.asToken("0.0.2");
	private static final TokenID tokenA = IdUtils.asToken("0.0.3");
	private static final TokenID tokenB = IdUtils.asToken("0.0.4");
	private static final AccountID sponsor = IdUtils.asAccount("0.0.5");
	private static final AccountID beneficiary = IdUtils.asAccount("0.0.6");
	private static final AccountID magician = IdUtils.asAccount("0.0.7");
	private static final AccountID spender = IdUtils.asAccount("0.0.8");
	private static final AccountID owner =  IdUtils.asAccount("0.0.9");
	private static final EntityNum spenderNum = EntityNum.fromAccountId(spender);
	private static final EntityNum ownerNum = EntityNum.fromAccountId(owner);
	private static final List<TokenAssociation> newRelationships = List.of(new FcTokenAssociation(
			10, 11).toGrpc());

	private static final EntityId feeCollector = new EntityId(1, 2, 8);
	private static final EntityId token = new EntityId(1, 2, 9);
	private static final long units = 123L;
	private static final long initialAllowance = 100L;

	private static final TokenTransferList nftTokenTransfers = TokenTransferList.newBuilder()
			.setToken(nft)
			.addNftTransfers(
					withNftAdjustments(nft, sponsor, beneficiary, 1L, sponsor, beneficiary, 2L, sponsor, beneficiary,
							3L).getNftTransfers(0)
			)
			.build();
	private static final TokenTransferList aTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenA)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	private static final TokenTransferList bTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenB)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	private static final ScheduleID scheduleID = IdUtils.asSchedule("5.6.7");
	private static final FcAssessedCustomFee balanceChange =
			new FcAssessedCustomFee(feeCollector, token, units, new long[] { 234L });
	private static final FcTokenAllowance nftAllowance1 = FcTokenAllowance.from(true);
	private static final FcTokenAllowance nftAllowance2 = FcTokenAllowance.from(List.of(1L, 2L));
	private static final FcTokenAllowanceId fungibleAllowanceId =
			FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenA), spenderNum);
	private static final FcTokenAllowanceId nftAllowanceId =
			FcTokenAllowanceId.from(EntityNum.fromTokenId(nft), spenderNum);
	private static final Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances = new TreeMap<>() {{
		put(ownerNum, new TreeMap<>() {{
			put(spenderNum, initialAllowance);
		}});
	}};
	private static final Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleAllowances = new TreeMap<>() {{
		put(ownerNum, new TreeMap<>() {{
			put(fungibleAllowanceId, initialAllowance);
		}});
	}};
	private static final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
			.setOwner(owner)
			.setAmount(initialAllowance)
			.setSpender(spender)
			.build();
	private static final TokenAllowance fungibleTokenAllowance = TokenAllowance.newBuilder()
			.setOwner(owner)
			.setAmount(initialAllowance)
			.setSpender(spender)
			.setTokenId(tokenA)
			.build();
	private static final NftAllowance nftAllowanceAll = NftAllowance.newBuilder()
			.setOwner(owner)
			.setApprovedForAll(BoolValue.of(true))
			.setTokenId(tokenA)
			.setSpender(spender)
			.build();
	private static final NftAllowance nftAllowanceSome = NftAllowance.newBuilder()
			.setOwner(owner)
			.setApprovedForAll(BoolValue.of(false))
			.setTokenId(nft)
			.addAllSerialNumbers(List.of(1L, 2L))
			.setSpender(spender)
			.build();

	private DomainSerdes serdes;
	private ExpirableTxnRecord subject;

	@BeforeEach
	void setup() {
		subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		serdes = mock(DomainSerdes.class);

		ExpirableTxnRecord.serdes = serdes;
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfers() {
		final var s = ExpirableTxnRecordTestHelper.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.build());
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
		final var source = grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		final var s = ExpirableTxnRecordTestHelper.fromGprc(source);
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static TransactionRecord grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
		return DomainSerdesTest.recordOne().asGrpc().toBuilder()
				.setTransactionHash(ByteString.copyFrom(pretendHash))
				.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
				.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers, nftTokenTransfers))
				.setScheduleRef(scheduleID)
				.addAssessedCustomFees(balanceChange.toGrpc())
				.addAllAutomaticTokenAssociations(newRelationships)
				.setAlias(ByteString.copyFromUtf8("test"))
				.build();
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfersAndScheduleRefCustomFees() {
		final var s = fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.setScheduleRef(scheduleID)
						.addAssessedCustomFees(balanceChange.toGrpc())
						.setAlias(ByteString.copyFrom("test".getBytes(StandardCharsets.UTF_8)))
						.build());
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static void setNonGrpcDefaultsOn(final ExpirableTxnRecord subject) {
		subject.setExpiry(expiry);
		subject.setSubmittingMember(submittingMember);
		subject.setNumChildRecords(numChildRecords);
		subject.setPackedParentConsensusTime(packedParentConsTime);
	}

	@Test
	void hashableMethodsWork() {
		final var pretend = mock(Hash.class);

		subject.setHash(pretend);

		assertEquals(pretend, subject.getHash());
	}

	@Test
	void fastCopyableWorks() {
		assertTrue(subject.isImmutable());
		assertSame(subject, subject.copy());
		assertDoesNotThrow(subject::release);
	}

	@Test
	void serializeWorksWithBothChildAndParentMetaAndAllowanceMaps() throws IOException {
		final var fout = mock(SerializableDataOutputStream.class);
		final var inOrder = Mockito.inOrder(serdes, fout);
		subject.setCryptoAllowances(cryptoAllowances);
		subject.setFungibleTokenAllowances(fungibleAllowances);

		subject.serialize(fout);

		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableInstant(subject.getConsensusTime(), fout);
		inOrder.verify(serdes).writeNullableString(subject.getMemo(), fout);
		inOrder.verify(fout).writeLong(subject.getFee());
		inOrder.verify(serdes).writeNullableSerializable(subject.getHbarAdjustments(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCallResult(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCreateResult(), fout);
		inOrder.verify(fout).writeLong(subject.getExpiry());
		inOrder.verify(fout).writeLong(subject.getSubmittingMember());
		inOrder.verify(fout).writeSerializableList(
				subject.getTokens(), true, true);
		inOrder.verify(fout).writeSerializableList(
				subject.getTokenAdjustments(), true, true);
		inOrder.verify(serdes).writeNullableSerializable(EntityId.fromGrpcScheduleId(scheduleID), fout);
		inOrder.verify(fout).writeSerializableList(
				subject.getNftTokenAdjustments(), true, true);
		inOrder.verify(fout).writeSerializableList(subject.getCustomFeesCharged(), true, true);
		inOrder.verify(fout).writeBoolean(true);
		inOrder.verify(fout).writeShort(numChildRecords);
		inOrder.verify(fout).writeBoolean(true);
		inOrder.verify(fout).writeLong(packedParentConsTime);
		inOrder.verify(fout).writeByteArray(subject.getAlias().toByteArray());
		inOrder.verify(fout).writeInt(cryptoAllowances.size());
		inOrder.verify(fout).writeLong(spenderNum.longValue());
		inOrder.verify(fout).writeLong(initialAllowance);
		inOrder.verify(fout).writeInt(fungibleAllowances.size());
		inOrder.verify(fout).writeSerializable(fungibleAllowanceId, true);
		inOrder.verify(fout).writeLong(initialAllowance);
	}

	@Test
	void serializeWorksWithNeitherChildAndParentMeta() throws IOException {
		final var fout = mock(SerializableDataOutputStream.class);
		final var inOrder = Mockito.inOrder(serdes, fout);

		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		subject.serialize(fout);

		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableInstant(subject.getConsensusTime(), fout);
		inOrder.verify(serdes).writeNullableString(subject.getMemo(), fout);
		inOrder.verify(fout).writeLong(subject.getFee());
		inOrder.verify(serdes).writeNullableSerializable(subject.getHbarAdjustments(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCallResult(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCreateResult(), fout);
		inOrder.verify(fout).writeLong(subject.getExpiry());
		inOrder.verify(fout).writeLong(subject.getSubmittingMember());
		inOrder.verify(fout).writeSerializableList(
				subject.getTokens(), true, true);
		inOrder.verify(fout).writeSerializableList(
				subject.getTokenAdjustments(), true, true);
		inOrder.verify(serdes).writeNullableSerializable(EntityId.fromGrpcScheduleId(scheduleID), fout);
		inOrder.verify(fout).writeSerializableList(
				subject.getNftTokenAdjustments(), true, true);
		inOrder.verify(fout).writeSerializableList(subject.getCustomFeesCharged(), true, true);
		inOrder.verify(fout, times(2)).writeBoolean(false);
		inOrder.verify(fout).writeByteArray(subject.getAlias().toByteArray());
	}

	@Test
	void serializableDetWorks() {
		assertEquals(ExpirableTxnRecord.MERKLE_VERSION, subject.getVersion());
		assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void asGrpcWorks() {
		final var expected = grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations()
				.toBuilder()
				.setParentConsensusTimestamp(MiscUtils.asTimestamp(packedParentConsTime))
				.addCryptoAdjustments(cryptoAllowance)
				.addTokenAdjustments(fungibleTokenAllowance)
				.build();

		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setExpiry(0L);
		subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);
		subject.setFungibleTokenAllowances(fungibleAllowances);
		subject.setCryptoAllowances(cryptoAllowances);

		final var grpcSubject = subject.asGrpc();

		var multiple = allToGrpc(List.of(subject, subject));

		assertEquals(expected, grpcSubject);
		assertEquals(List.of(expected, expected), multiple);
	}

	@Test
	void makeupNftAdjustmentsUsesNullForNullFungibleAdjusts() {
		assertNull(subject.makeupNftAdjustsMatching(null));
	}

	@Test
	void nullEqualsWorks() {
		assertNotEquals(null, subject);
		assertEquals(subject, subject);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = DomainSerdesTest.recordOne();
		final var three = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertEquals(one, three);
		assertNotEquals(one, two);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void toStringWorksWithParentConsTime() {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		final var desired = "ExpirableTxnRecord{numChildRecords=2, receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, fee=555, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false, nonce=0}, " +
				"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, submittingMember=1, " +
				"memo=Alpha bravo charlie, contractCreation=EvmFnResult{gasUsed=55, bloom=, result=, error=null," +
				" " +
				"contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
				"logs=[EvmLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}], " +
				"stateChanges={}, evmAddress=, gas=1000000, amount=0, functionParameters=53656e7369626c6521}, " +
				"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, " +
				"scheduleRef=EntityId{shard=5, realm=6, num=7}, alias=test, parentConsensusTime=1970-01-15T06:56:07" +
				".000000890Z, tokenAdjustments=0.0.3(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <-" +
				" " +
				"+1000]}), 0.0.4(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.2" +
				"(NftAdjustments{readable=[1 0.0.5 0.0.6]}), assessedCustomFees=" +
				"(FcAssessedCustomFee{token=EntityId{shard=1, realm=2, num=9}, account=EntityId{shard=1, realm=2, " +
				"num=8}, units=123, effective payer accounts=[234]}), newTokenAssociations=" +
				"(FcTokenAssociation{token=10, account=11})}";

		assertEquals(desired, subject.toString());
	}

	@Test
	void toStringWorksWithoutParentConsTime() {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		final var desired = "ExpirableTxnRecord{numChildRecords=2, receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, fee=555, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false, nonce=0}, " +
				"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, submittingMember=1, " +
				"memo=Alpha bravo charlie, contractCreation=EvmFnResult{gasUsed=55, bloom=, result=, error=null, " +
				"contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
				"logs=[EvmLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}], " +
				"stateChanges={}, evmAddress=, gas=1000000, amount=0, functionParameters=53656e7369626c6521}, " +
				"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, " +
				"scheduleRef=EntityId{shard=5, realm=6, num=7}, alias=test, tokenAdjustments=0.0.3" +
				"(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.4" +
				"(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.2" +
				"(NftAdjustments{readable=[1 0.0.5 0.0.6]}), assessedCustomFees=" +
				"(FcAssessedCustomFee{token=EntityId{shard=1, realm=2, num=9}, account=EntityId{shard=1, realm=2, " +
				"num=8}, units=123, effective payer accounts=[234]}), newTokenAssociations=" +
				"(FcTokenAssociation{token=10, account=11})}";
		assertEquals(desired, subject.toString());
	}

	@Test
	void toStringWorksWithAllowanceMaps() {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setCryptoAllowances(cryptoAllowances);
		subject.setFungibleTokenAllowances(fungibleAllowances);
		final var expected = "ExpirableTxnRecord{numChildRecords=2, receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, fee=555, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false, nonce=0}, " +
				"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, submittingMember=1, " +
				"memo=Alpha bravo charlie, contractCreation=EvmFnResult{gasUsed=55, bloom=, result=, error=null," +
				" " +
				"contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
				"logs=[EvmLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}], " +
				"stateChanges={}, evmAddress=, gas=1000000, amount=0, functionParameters=53656e7369626c6521}, " +
				"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, " +
				"scheduleRef=EntityId{shard=5, realm=6, num=7}, alias=test, parentConsensusTime=1970-01-15T06:56:07" +
				".000000890Z, tokenAdjustments=0.0.3(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <-" +
				" " +
				"+1000]}), 0.0.4(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.2" +
				"(NftAdjustments{readable=[1 0.0.5 0.0.6]}), assessedCustomFees=" +
				"(FcAssessedCustomFee{token=EntityId{shard=1, realm=2, num=9}, account=EntityId{shard=1, realm=2, " +
				"num=8}, units=123, effective payer accounts=[234]}), newTokenAssociations=" +
				"(FcTokenAssociation{token=10, account=11}), " +
				"cryptoAllowances=[{owner : EntityNum{value=9}, spender : EntityNum{value=8}, allowance : 100}], " +
				"fungibleTokenAllowances=[{owner : EntityNum{value=9}, token : EntityNum{value=3}, " +
				"spender : EntityNum{value=8}, allowance : 100}]}";

		assertEquals(expected, subject.toString());
	}

	@AfterEach
	void cleanup() {
		ExpirableTxnRecord.serdes = new DomainSerdes();
	}
}
