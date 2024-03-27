/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event;

import static com.swirlds.platform.system.events.EventConstants.BIRTH_ROUND_UNDEFINED;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

/**
 * A builder for creating event instances for testing purposes.
 */
public class TestingEventBuilder {
    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochMilli(1588771316678L);
    private static final SoftwareVersion DEFAULT_SOFTWARE_VERSION = new BasicSoftwareVersion(1);
    private static final NodeId DEFAULT_CREATOR_ID = new NodeId(0);
    private static final int DEFAULT_APP_TRANSACTION_COUNT = 2;
    private static final int DEFAULT_SYSTEM_TRANSACTION_COUNT = 0;
    private static final int DEFAULT_TRANSACTION_BYTES = 4;

    private final Random random;

    /**
     * Creator ID to use.
     * <p>
     * If not set, defaults to the same creator ID as the self parent. If the self parent is not set, defaults to
     * {@link #DEFAULT_CREATOR_ID}.
     */
    private NodeId creatorId;

    /**
     * The time created of the event.
     * <p>
     * If not set, defaults to the time created as the self parent, plus a random number of milliseconds between
     * 1 and 99 inclusive. If the self parent is not set, defaults to {@link #DEFAULT_TIMESTAMP}.
     */
    private Instant timeCreated;

    /**
     * The number of app transactions an event should contain.
     * <p>
     * If not set, defaults to {@link #DEFAULT_APP_TRANSACTION_COUNT}.
     */
    private Integer appTransactionCount;

    /**
     * The number of system transactions an event should contain.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SYSTEM_TRANSACTION_COUNT}.
     */
    private Integer systemTransactionCount;

    /**
     * The size in bytes of each transaction.
     * <p>
     * If not set, defaults to {@link #DEFAULT_TRANSACTION_BYTES}.
     */
    private Integer transactionSize;

    /**
     * The transactions to be contained in the event.
     * <p>
     * If not set, transactions will be auto generated, based on configured settings.
     */
    private ConsensusTransactionImpl[] transactions;

    /**
     * The self parent of the event. May be null.
     */
    private GossipEvent selfParent;

    /**
     * The other parent of the event. May be null.
     * <p>
     * Future work: add support for multiple other parents.
     */
    private GossipEvent otherParent;

    /**
     * Overrides the generation of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     */
    private Long selfParentGenerationOverride;

    /**
     * Overrides the generation of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     */
    private Long otherParentGenerationOverride;

    /**
     * The birth round of the event.
     * <p>
     * If not set, defaults to the maximum of the birth rounds of the self and other parents, plus a random number
     * between 0 and 2 inclusive.
     */
    private Long birthRound;

    /**
     * The software version of the event.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SOFTWARE_VERSION}.
     */
    private SoftwareVersion softwareVersion;

    /**
     * Constructor
     *
     * @param random a source of randomness
     */
    public TestingEventBuilder(@NonNull final Random random) {
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Set the creator ID to use.
     * <p>
     * If not set, defaults to the same creator ID as the self parent. If the self parent is not set, defaults to
     * {@link #DEFAULT_CREATOR_ID}.
     *
     * @param creatorId the creator ID
     * @return this instance
     */
    public @NonNull TestingEventBuilder setCreatorId(@Nullable final NodeId creatorId) {
        this.creatorId = creatorId;
        return this;
    }

    /**
     * Set the software version of the event.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SOFTWARE_VERSION}.
     *
     * @param softwareVersion the software version
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSoftwareVersion(@Nullable final SoftwareVersion softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    /**
     * Set the time created of an event.
     * <p>
     * If not set, defaults to the time created as the self parent, plus a random number of milliseconds between
     * 1 and 99 inclusive. If the self parent is not set, defaults to {@link #DEFAULT_TIMESTAMP}.
     *
     * @param timeCreated the time created
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTimeCreated(@Nullable final Instant timeCreated) {
        this.timeCreated = timeCreated;
        return this;
    }

    /**
     * Set the number of app transactions an event should contain.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param numberOfAppTransactions the number of app transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setAppTransactionCount(final int numberOfAppTransactions) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set app transaction count when transactions are explicitly set");
        }

        this.appTransactionCount = numberOfAppTransactions;
        return this;
    }

    /**
     * Set the number of system transactions an event should contain.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param numberOfSystemTransactions the number of system transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setNumberOfSystemTransactions(final int numberOfSystemTransactions) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set system transaction count when transactions are explicitly set");
        }

        this.systemTransactionCount = numberOfSystemTransactions;
        return this;
    }

    /**
     * Set the transaction size.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param transactionSize the transaction size
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactionSize(final int transactionSize) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set transaction size when transactions are explicitly set");
        }

        this.transactionSize = transactionSize;
        return this;
    }

    /**
     * Explicitly set the transactions of an event.
     * <p>
     * Throws an exception if app transaction count, system transaction count, or transaction size are set.
     *
     * @param transactions the transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactions(@Nullable final ConsensusTransactionImpl[] transactions) {
        if (appTransactionCount != null || systemTransactionCount != null || transactionSize != null) {
            throw new IllegalStateException(
                    "Cannot set transactions when app transaction count, system transaction count, or transaction "
                            + "size are explicitly set");
        }

        this.transactions = transactions;
        return this;
    }

    /**
     * Set the self-parent of an event.
     * <p>
     * If not set, a self parent will NOT be generated: the output event will have a null self parent.
     *
     * @param selfParent the self-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSelfParent(@Nullable final GossipEvent selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    /**
     * Set the other-parent of an event
     * <p>
     * If not set, an other parent will NOT be generated: the output event will have a null other parent.
     *
     * @param otherParent the other-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setOtherParent(@Nullable final GossipEvent otherParent) {
        this.otherParent = otherParent;
        return this;
    }

    /**
     * Override the generation of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     *
     * @param generation the generation to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideSelfParentGeneration(final long generation) {
        this.selfParentGenerationOverride = generation;
        return this;
    }

    /**
     * Override the generation of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     *
     * @param generation the generation to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideOtherParentGeneration(final long generation) {
        this.otherParentGenerationOverride = generation;
        return this;
    }

    /**
     * Set the birth round of an event.
     * <p>
     * If not set, defaults to the maximum of the birth rounds of the self and other parents, plus a random number
     * between 0 and 2 inclusive.
     *
     * @param birthRound the birth round to set
     * @return this instance
     */
    public @NonNull TestingEventBuilder setBirthRound(final long birthRound) {
        this.birthRound = birthRound;
        return this;
    }

    /**
     * Generate transactions based on the settings provided.
     * <p>
     * Only utilized if the transactions are not set with {@link #setTransactions}.
     *
     * @return the generated transactions
     */
    private ConsensusTransactionImpl[] generateTransactions() {
        if (appTransactionCount == null) {
            appTransactionCount = DEFAULT_APP_TRANSACTION_COUNT;
        }

        if (systemTransactionCount == null) {
            systemTransactionCount = DEFAULT_SYSTEM_TRANSACTION_COUNT;
        }

        final ConsensusTransactionImpl[] generatedTransactions =
                new ConsensusTransactionImpl[appTransactionCount + systemTransactionCount];

        if (transactionSize == null) {
            transactionSize = DEFAULT_TRANSACTION_BYTES;
        }

        for (int i = 0; i < appTransactionCount; ++i) {
            final byte[] bytes = new byte[transactionSize];
            random.nextBytes(bytes);
            generatedTransactions[i] = new SwirldTransaction(bytes);
        }

        for (int i = appTransactionCount; i < appTransactionCount + systemTransactionCount; ++i) {
            generatedTransactions[i] = new StateSignatureTransaction(
                    random.nextLong(0, Long.MAX_VALUE),
                    RandomUtils.randomSignature(random),
                    RandomUtils.randomHash(random));
        }

        return generatedTransactions;
    }

    /**
     * Create an event descriptor from a parent event.
     *
     * @param parent the parent event
     * @return the parent event descriptor
     */
    @Nullable
    private EventDescriptor createDescriptorFromParent(
            @Nullable final GossipEvent parent, @Nullable final Long generationOverride) {
        if (parent == null) {
            return null;
        }

        final long generation = generationOverride == null ? parent.getGeneration() : generationOverride;

        return new EventDescriptor(
                parent.getHashedData().getHash(),
                parent.getHashedData().getCreatorId(),
                generation,
                parent.getHashedData().getBirthRound());
    }

    /**
     * Build the event
     *
     * @return the new event
     */
    public @NonNull GossipEvent build() {
        if (softwareVersion == null) {
            softwareVersion = DEFAULT_SOFTWARE_VERSION;
        }

        if (creatorId == null) {
            if (selfParent != null) {
                creatorId = selfParent.getHashedData().getCreatorId();
            } else {
                creatorId = DEFAULT_CREATOR_ID;
            }
        }

        final EventDescriptor selfParentDescriptor =
                createDescriptorFromParent(selfParent, selfParentGenerationOverride);
        final EventDescriptor otherParentDescriptor =
                createDescriptorFromParent(otherParent, otherParentGenerationOverride);

        if (this.birthRound == null) {
            final long maxParentBirthRound = Math.max(
                    selfParent == null
                            ? BIRTH_ROUND_UNDEFINED
                            : selfParent.getHashedData().getBirthRound(),
                    otherParent == null
                            ? BIRTH_ROUND_UNDEFINED
                            : otherParent.getHashedData().getBirthRound());

            // randomly add between 0 and 2 to max parent birth round
            birthRound = maxParentBirthRound + random.nextLong(0, 3);
        }

        if (timeCreated == null) {
            if (selfParent == null) {
                timeCreated = DEFAULT_TIMESTAMP;
            } else {
                // randomly add between 1 and 99 milliseconds to self parent time created
                timeCreated = selfParent.getHashedData().getTimeCreated().plusMillis(random.nextLong(1, 100));
            }
        }

        if (transactions == null) {
            transactions = generateTransactions();
        }

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                softwareVersion,
                creatorId,
                selfParentDescriptor,
                // Future work: add support for multiple other parents
                otherParentDescriptor == null
                        ? Collections.emptyList()
                        : Collections.singletonList(otherParentDescriptor),
                birthRound,
                timeCreated,
                transactions);
        hashedData.setHash(RandomUtils.randomHash(random));

        final byte[] signature = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(signature);

        final NodeId otherParentCreatorId;
        if (otherParent == null) {
            otherParentCreatorId = null;
        } else {
            otherParentCreatorId = otherParent.getHashedData().getCreatorId();
        }

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(otherParentCreatorId, signature);
        final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);
        gossipEvent.buildDescriptor();

        return gossipEvent;
    }
}
