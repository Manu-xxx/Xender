/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle;

/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.utility.Labeled;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.platform.state.merkle.memory.InMemoryKey;
import com.swirlds.platform.state.merkle.memory.InMemoryValue;
import com.swirlds.platform.state.merkle.queue.QueueNode;
import com.swirlds.platform.state.merkle.singleton.SingletonNode;
import com.swirlds.platform.test.fixtures.state.StateTestBase;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.AfterEach;

/**
 * This base class provides helpful methods and defaults for simplifying the other merkle related
 * tests in this and sub packages. It is highly recommended to extend from this class.
 *
 * <h1>Services</h1>
 *
 * <p>This class introduces two real services, and one bad service. The real services are called
 * (quite unhelpfully) {@link #FIRST_SERVICE} and {@link #SECOND_SERVICE}. There is also an {@link
 * #UNKNOWN_SERVICE} which is useful for tests where we are trying to look up a service that should
 * not exist.
 *
 * <p>Each service has a number of associated states, based on those defined in {@link
 * StateTestBase}. The {@link #FIRST_SERVICE} has "fruit" and "animal" states, while the {@link
 * #SECOND_SERVICE} has space, steam, and country themed states. Most of these are simple String
 * types for the key and value, but the space themed state uses Long as the key type.
 *
 * <p>This class defines all the {@link Codec}, {@link StateMetadata}, and {@link MerkleMap}s
 * required to represent each of these. It does not create a {@link VirtualMap} automatically, but
 * does provide APIs to make it easy to create them (the {@link VirtualMap} has a lot of setup
 * complexity, and also requires a storage directory, so rather than creating these for every test
 * even if they don't need it, I just use it for virtual map specific tests).
 */
public class MerkleTestBase extends com.swirlds.platform.test.fixtures.state.merkle.MerkleTestBase {

    protected StateMetadata<String, String> fruitMetadata;
    protected StateMetadata<String, String> fruitVirtualMetadata;
    protected StateMetadata<String, String> animalMetadata;

    // The "SPACE" map is part of SECOND_SERVICE and uses the long-based keys
    protected String spaceLabel;
    protected StateMetadata<Long, String> spaceMetadata;
    protected MerkleMap<InMemoryKey<Long>, InMemoryValue<Long, Long>> spaceMerkleMap;

    // The "STEAM" queue is part of FIRST_SERVICE
    protected String steamLabel;
    protected StateMetadata<String, String> steamMetadata;
    protected QueueNode<String> steamQueue;

    // The "COUNTRY" singleton is part of FIRST_SERVICE
    protected String countryLabel;
    protected StateMetadata<String, String> countryMetadata;
    protected SingletonNode<String> countrySingleton;

    /** Sets up the "Fruit" merkle map, label, and metadata. */
    protected void setupFruitMerkleMap() {
        super.setupFruitMerkleMap();
        fruitMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.inMemory(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC));
    }

    /** Sets up the "Fruit" virtual map, label, and metadata. */
    protected void setupFruitVirtualMap() {
        super.setupFruitVirtualMap();
        fruitVirtualMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.onDisk(FRUIT_STATE_KEY, STRING_CODEC, STRING_CODEC, 100));
    }

    /** Sets up the "Animal" merkle map, label, and metadata. */
    protected void setupAnimalMerkleMap() {
        super.setupAnimalMerkleMap();
        animalMetadata = new StateMetadata<>(
                FIRST_SERVICE,
                new TestSchema(1),
                StateDefinition.inMemory(ANIMAL_STATE_KEY, STRING_CODEC, STRING_CODEC));
    }

    /** Sets up the "Space" merkle map, label, and metadata. */
    protected void setupSpaceMerkleMap() {
        super.setupSpaceMerkleMap();
        spaceMetadata = new StateMetadata<>(
                SECOND_SERVICE, new TestSchema(1), StateDefinition.inMemory(SPACE_STATE_KEY, LONG_CODEC, STRING_CODEC));
    }

    protected void setupSingletonCountry() {
        super.setupSingletonCountry();
        countryMetadata = new StateMetadata<>(
                FIRST_SERVICE, new TestSchema(1), StateDefinition.singleton(COUNTRY_STATE_KEY, STRING_CODEC));
    }

    /** Creates a new arbitrary virtual map with the given label, storageDir, and metadata */
    @SuppressWarnings("unchecked")
    protected VirtualMap<OnDiskKey<String>, OnDiskValue<String>> createVirtualMap(
            String label, StateMetadata<String, String> md) {
        return createVirtualMap(
                label,
                md.onDiskKeySerializerClassId(),
                md.stateDefinition().keyCodec(),
                md.onDiskValueSerializerClassId(),
                md.stateDefinition().valueCodec());
    }

    /**
     * Looks within the merkle tree for a node with the given label. This is useful for tests that
     * need to verify some change actually happened in the merkle tree.
     */
    protected MerkleNode getNodeForLabel(MerkleHederaState hederaMerkle, String label) {
        // This is not idea, as it requires white-box testing -- knowing the
        // internal details of the MerkleHederaState. But lacking a getter
        // (which I don't want to add), this is what I'm left with!
        for (int i = 0, n = hederaMerkle.getNumberOfChildren(); i < n; i++) {
            final MerkleNode child = hederaMerkle.getChild(i);
            if (child instanceof Labeled labeled && label.equals(labeled.getLabel())) {
                return child;
            }
        }

        return null;
    }

    /** A convenience method for adding a k/v pair to a merkle map */
    protected void add(
            MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> map,
            StateMetadata<String, String> md,
            String key,
            String value) {
        final var def = md.stateDefinition();
        super.add(map, md.inMemoryValueClassId(), def.keyCodec(), def.valueCodec(), key, value);
    }

    /** A convenience method for adding a k/v pair to a virtual map */
    protected void add(
            VirtualMap<OnDiskKey<String>, OnDiskValue<String>> map,
            StateMetadata<String, String> md,
            String key,
            String value) {
        super.add(
                map,
                md.onDiskKeyClassId(),
                md.stateDefinition().keyCodec(),
                md.onDiskValueClassId(),
                md.stateDefinition().valueCodec(),
                key,
                value);
    }

    @AfterEach
    void cleanUp() {
        MerkleDb.resetDefaultInstancePath();
    }
}
