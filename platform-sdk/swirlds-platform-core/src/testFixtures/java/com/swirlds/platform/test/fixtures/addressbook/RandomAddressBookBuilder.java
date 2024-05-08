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

package com.swirlds.platform.test.fixtures.addressbook;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * A utility for generating a random address book.
 */
public class RandomAddressBookBuilder {

    // TODO add a way to generate with real keys

    /**
     * All randomness comes from this.
     */
    private final Random random;

    private final Set<NodeId> nodeIds = new HashSet<>();

    /**
     * The number of addresses to put into the address book.
     */
    private int size = 4;

    /**
     * Describes different ways that the random address book has its weight distributed if the custom strategy lambda is
     * unset.
     */
    public enum WeightDistributionStrategy {
        /**
         * All nodes have equal weight.
         */
        BALANCED,
        /**
         * Nodes are given weight with a gaussian distribution.
         */
        GAUSSIAN
    }

    /**
     * The weight distribution strategy.
     */
    private WeightDistributionStrategy weightDistributionStrategy = WeightDistributionStrategy.GAUSSIAN;

    /**
     * The average weight. Used directly if using {@link WeightDistributionStrategy#BALANCED}, used as mean if using
     * {@link WeightDistributionStrategy#GAUSSIAN}.
     */
    private long averageWeight = 1000;

    /**
     * The standard deviation of the weight, ignored if distribution strategy is not
     * {@link WeightDistributionStrategy#GAUSSIAN}.
     */
    private long weightStandardDeviation = 100;

    /**
     * The minimum weight to give to any particular address.
     */
    private long minimumWeight = 1;

    /**
     * The maximum weight to give to any particular address.
     */
    private long maximumWeight = 100_000;

    /**
     * Used to determine weight for each node. Overrides all other behaviors if set.
     */
    private Function<NodeId, Long> customWeightGenerator;

    /**
     * the next available node id for new addresses.
     */
    private NodeId nextNodeId = NodeId.FIRST_NODE_ID;

    /**
     * Create a new random address book generator.
     *
     * @param random a source of randomness
     * @return a new random address book generator
     */
    @NonNull
    public static RandomAddressBookBuilder create(@NonNull final Random random) {
        return new RandomAddressBookBuilder(random);
    }

    /**
     * Constructor.
     *
     * @param random a source of randomness
     */
    private RandomAddressBookBuilder(@NonNull final Random random) {
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Generate the next node ID.
     */
    private NodeId getNextNodeId() {
        final NodeId nextId = this.nextNodeId;
        // randomly advance between 1 and 3 steps
        final int randomAdvance = random.nextInt(3);
        this.nextNodeId = this.nextNodeId.getOffset(randomAdvance + 1L);
        return nextId;
    }

    /**
     * Generate the next weight for the next address.
     */
    private long getNextWeight(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "NodeId must not be null");

        if (customWeightGenerator != null) {
            return customWeightGenerator.apply(nodeId);
        }

        final long unboundedWeight;
        switch (weightDistributionStrategy) {
            case BALANCED -> unboundedWeight = averageWeight;
            case GAUSSIAN -> unboundedWeight =
                    Math.max(0, (long) (averageWeight + random.nextGaussian() * weightStandardDeviation));
            default -> throw new IllegalStateException("Unexpected value: " + weightDistributionStrategy);
        }

        return Math.min(maximumWeight, Math.max(minimumWeight, unboundedWeight));
    }

    /**
     * Build a random address book given the provided configuration.
     */
    @NonNull
    public AddressBook build() {
        final AddressBook addressBook = new AddressBook();
        addressBook.setNextNodeId(this.nextNodeId);
        addressBook.setRound(Math.abs(random.nextLong()));

        addToAddressBook(addressBook);
        return addressBook;
    }

    // TODO remove (or at least make private)

    /**
     * Add new addresses to an address book. The number of addresses is equal to the value specified by
     * {@link #withSize(int)}. The next candidate ID is set to be the address book's
     * {@link AddressBook#getNextNodeId()}.
     *
     * @param addressBook the address book to add new addresses to
     * @return the input address book after it has been expanded
     */
    @NonNull
    public AddressBook addToAddressBook(final AddressBook addressBook) {
        nextNodeId = addressBook.getNextNodeId();

        if (!nodeIds.isEmpty()) {
            nodeIds.stream().sorted().forEach(nodeId -> addressBook.add(buildNextAddress(nodeId)));
        } else {
            for (int index = 0; index < size; index++) {
                addressBook.add(buildNextAddress());
            }
        }

        CryptographyHolder.get().digestSync(addressBook);

        return addressBook;
    }

    // TODO remove, this doesn't belong here

    /**
     * Remove a number of addresses from an address book.
     *
     * @param addressBook the address book to remove from
     * @param count       the number of addresses to remove, removes all addresses if count exceeds address book size
     * @return the input address book
     */
    @NonNull
    public AddressBook removeFromAddressBook(@NonNull final AddressBook addressBook, final int count) {
        Objects.requireNonNull(addressBook, "AddressBook must not be null");
        final List<NodeId> nodeIds = new ArrayList<>(addressBook.getSize());
        addressBook.forEach((final Address address) -> nodeIds.add(address.getNodeId()));
        Collections.shuffle(nodeIds, random);
        for (int i = 0; i < count && i < nodeIds.size(); i++) {
            addressBook.remove(nodeIds.get(i));
        }
        return addressBook;
    }

    /**
     * Build a random address using provided configuration. Address IS NOT automatically added to the address book.
     *
     * @return a random address
     */
    @NonNull
    public Address buildNextAddress() {
        return buildNextAddress(null);
    }

    /**
     * Build a random address using provided configuration. Address IS NOT automatically added to the address book.
     *
     * @return a random address
     */
    @NonNull
    private Address buildNextAddress(@Nullable final NodeId suppliedNodeId) {
        final NodeId nodeId = suppliedNodeId == null ? getNextNodeId() : suppliedNodeId;

        return RandomAddressBuilder.create(random)
                .withNodeId(nodeId)
                .withWeight(getNextWeight(nodeId))
                .build();
    }

    /**
     * Set the size of the address book.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withSize(final int size) {
        this.size = size;
        return this;
    }

    /**
     * Set the node IDs of the address book.
     *
     * @return this object
     */
    @NonNull // TODO maybe remove
    public RandomAddressBookBuilder withNodeIds(@NonNull final Set<NodeId> nodeIds) {
        Objects.requireNonNull(nodeIds, "NodeIds must not be null");
        this.nodeIds.clear();
        this.nodeIds.addAll(nodeIds);
        return this;
    }

    /**
     * Set the average weight for an address.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withAverageWeight(final long averageWeight) {
        this.averageWeight = averageWeight;
        return this;
    }

    /**
     * Set the standard deviation for the weight for an address.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withWeightStandardDeviation(final long weightStandardDeviation) {
        this.weightStandardDeviation = weightStandardDeviation;
        return this;
    }

    /**
     * Set the minimum weight for an address.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withMinimumWeight(final long minimumWeight) {
        this.minimumWeight = minimumWeight;
        return this;
    }

    /**
     * Set the maximum weight for an address.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withMaximumWeight(final long maximumWeight) {
        this.maximumWeight = maximumWeight;
        return this;
    }

    /**
     * Provide a method that is used to determine the weight of each node. Overrides all other weight generation
     * settings if set.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withCustomWeightGenerator(final Function<NodeId, Long> customWeightGenerator) {
        this.customWeightGenerator = customWeightGenerator;
        return this;
    }

    /**
     * Set the strategy used for deciding distribution of weight.
     *
     * @return this object
     */
    @NonNull
    public RandomAddressBookBuilder withWeightDistributionStrategy(
            final WeightDistributionStrategy weightDistributionStrategy) {

        this.weightDistributionStrategy = weightDistributionStrategy;
        return this;
    }
}
