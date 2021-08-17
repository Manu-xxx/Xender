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

import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class FcTokenAssociationTest {
	private final EntityId accountId = new EntityId(4, 5, 6);
	private final EntityId tokenId = new EntityId(1, 2, 3);
	private final FcTokenAssociation subject = new FcTokenAssociation(tokenId, accountId);
	private final TokenAssociation grpc = TokenAssociation.newBuilder()
			.setTokenId(tokenId.toGrpcTokenId())
			.setAccountId(accountId.toGrpcAccountId())
			.build();

	@Test
	void serializationWorks() throws IOException, ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		subject.serialize(dos);
		dos.flush();

		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		final var newSubject = new FcTokenAssociation();
		newSubject.deserialize(din, FcTokenAssociation.RELEASE_0180_VERSION);

		assertEquals(subject, newSubject);
	}

	@Test
	void toStringWorks() {
		assertEquals("FcTokenAssociation{token=EntityId{shard=1, realm=2, num=3}, account=EntityId{shard=4, realm=5, num=6}}",
				subject.toString());
	}

	@Test
	void toGrpcWorks() {
		assertEquals(grpc, subject.toGrpc());
	}

	@Test
	void fromGrpcWorks() {
		final var newSubject = FcTokenAssociation.fromGrpc(grpc);
		assertEquals(subject, newSubject);
	}
}
