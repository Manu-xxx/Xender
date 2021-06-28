/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.state.submerkle;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NftAdjustmentsTest {

	NftAdjustments subject;
	AccountID sender = AccountID.getDefaultInstance();
	AccountID recipient = AccountID.newBuilder().setAccountNum(3).setRealmNum(2).setShardNum(1).build();


	@BeforeEach
	void setUp() {
		subject = new NftAdjustments();
	}

	@Test
	void getClassId() {
		assertEquals(0xd7a02bf45e103466L, subject.getClassId());
	}

	@Test
	void getVersion() {
		assertEquals(1, subject.getVersion());
	}

	@Test
	void deserialize() throws IOException {
		SerializableDataInputStream stream = mock(SerializableDataInputStream.class);
		given(stream.readSerializableList(eq(NftAdjustments.MAX_NUM_ADJUSTMENTS), anyBoolean(), any())).willReturn(Collections.emptyList());
		given(stream.readLongArray(NftAdjustments.MAX_NUM_ADJUSTMENTS)).willReturn(new long[]{1, 2, 3});

		subject.deserialize(stream, 1);
		verify(stream).readLongArray(NftAdjustments.MAX_NUM_ADJUSTMENTS);
		verify(stream, times(2)).readSerializableList(eq(1024), eq(true), any());
	}

	@Test
	void serialize() throws IOException {
		givenTransferList();
		SerializableDataOutputStream stream = mock(SerializableDataOutputStream.class);
		subject.serialize(stream);
		verify(stream).writeLongArray(new long[]{1});
		verify(stream, times(1)).writeSerializableList(
				eq(List.of(EntityId.fromGrpcAccountId(sender))), eq(true), eq(true));
		verify(stream, times(1)).writeSerializableList(
				eq(List.of(EntityId.fromGrpcAccountId(recipient))), eq(true), eq(true));
	}

	@Test
	void testEquals() {
		NftAdjustments adjustments = new NftAdjustments();
		assertEquals(adjustments, subject);
		assertTrue(subject.equals(subject));
		assertFalse(subject.equals(null));
	}

	@Test
	void testHashCode() {
		assertEquals(new NftAdjustments().hashCode(), subject.hashCode());
	}

	@Test
	void toStringWorks() {
		givenTransferList();
		var str = "NftAdjustments{readable=[1 0.0.0 1.2.3]}";
		assertEquals(str, subject.toString());
	}

	@Test
	void toGrpc(){
		assertNotNull(subject.toGrpc());
		givenTransferList();
		var grpc =  subject.toGrpc();
		var transferList = grpc.getNftTransfersList();
		assertEquals(transferList.get(0).getSerialNumber(), 1);
		assertEquals(transferList.get(0).getSenderAccountID(), sender);
		assertEquals(transferList.get(0).getReceiverAccountID(), recipient);
	}

	@Test
	void fromGrpc(){
		givenTransferList();
		var grpc = List.of(NftTransfer.newBuilder()
				.setSerialNumber(1)
				.setReceiverAccountID(recipient).setSenderAccountID(sender).build());

		assertEquals(subject, NftAdjustments.fromGrpc(grpc));
	}

	private void givenTransferList(){
		subject = NftAdjustments.fromGrpc(List.of(
				NftTransfer
						.newBuilder()
						.setSerialNumber(1)
						.setSenderAccountID(sender)
						.setReceiverAccountID(recipient)
						.build()));
	}
}