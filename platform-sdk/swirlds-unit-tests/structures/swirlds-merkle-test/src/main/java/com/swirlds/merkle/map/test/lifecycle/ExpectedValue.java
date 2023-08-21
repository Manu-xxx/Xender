/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.test.pta.MapValue;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * Value stored in the ExpectedMap with corresponding MapKey
 */
public class ExpectedValue implements Serializable {
    /**
     * type of this entity
     */
    @JsonProperty
    private EntityType entityType;

    /**
     * Hash of the corresponding MapValue in MerkleMap
     */
    @JsonProperty
    private Hash hash;

    /**
     * It is set to true, if any error occurs while handling transactions for corresponding MapKey
     */
    @JsonProperty
    private boolean isErrored;

    /**
     * Last modified LifecycleStatus by handling a transaction.
     * This will also be updated during reconnect/restart/when account expires
     */
    @JsonProperty
    private LifecycleStatus latestHandledStatus;

    /**
     * Last modified LifecycleStatus by submitting or initializing a transaction.
     */
    @JsonProperty
    private LifecycleStatus latestSubmitStatus;

    /**
     * History of previous latestHandledStatus, which will help to
     * look-back the cause if any error happens while handling transactions
     */
    @JsonProperty
    private LifecycleStatus historyHandledStatus;

    @JsonProperty
    private long uid;

    public ExpectedValue(
            final EntityType entityType,
            final Hash hash,
            final boolean isErrored,
            final LifecycleStatus latestSubmitStatus,
            final LifecycleStatus latestHandledStatus,
            final LifecycleStatus historyHandledStatus,
            final long uid) {
        this.entityType = entityType;
        this.hash = hash;
        this.isErrored = isErrored;
        this.latestSubmitStatus = latestSubmitStatus;
        this.latestHandledStatus = latestHandledStatus;
        this.historyHandledStatus = historyHandledStatus;
        this.uid = uid;
    }

    public ExpectedValue(final EntityType entityType, final LifecycleStatus latestSubmitStatus) {
        this.entityType = entityType;
        this.hash = null;
        this.isErrored = false;
        this.latestHandledStatus = null;
        this.latestSubmitStatus = latestSubmitStatus;
        this.historyHandledStatus = null;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    // Empty constructor is needed by jackson for deserialization
    public ExpectedValue() {}

    public Hash getHash() {
        return hash;
    }

    public ExpectedValue setHash(Hash hash) {
        this.hash = hash;
        return this;
    }

    @JsonIgnore // Ignored because it serializes isErrored twice as jackson removes "is" prefix for boolean
    public boolean isErrored() {
        return isErrored;
    }

    public ExpectedValue setErrored(boolean errored) {
        isErrored = errored;
        return this;
    }

    public LifecycleStatus getLatestHandledStatus() {
        return latestHandledStatus;
    }

    public ExpectedValue setLatestHandledStatus(LifecycleStatus latestHandledStatus) {
        this.latestHandledStatus = latestHandledStatus;
        return this;
    }

    public LifecycleStatus getLatestSubmitStatus() {
        return latestSubmitStatus;
    }

    public ExpectedValue setLatestSubmitStatus(LifecycleStatus latestSubmitStatus) {
        this.latestSubmitStatus = latestSubmitStatus;
        return this;
    }

    public LifecycleStatus getHistoryHandledStatus() {
        return historyHandledStatus;
    }

    public ExpectedValue setHistoryHandledStatus(LifecycleStatus historyHandledStatus) {
        this.historyHandledStatus = historyHandledStatus;
        return this;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(final long uid) {
        this.uid = uid;
    }

    /**
     * Shows if ExpectedValue hash in ExpectedMap and the corresponding MapValue in original
     * MerkleMap are same if their hash matches
     * @param mapValue MapValue of corresponding ExpectedValue in MerkleMap
     * @return A boolean if the hash of ExpectedMapValue is same as MapValue
     */
    public boolean isHashMatch(MapValue mapValue) {
        return this.hash.equals(mapValue.getHash());
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, hash, isErrored, latestHandledStatus, latestSubmitStatus, historyHandledStatus);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        ExpectedValue that = (ExpectedValue) obj;
        return new EqualsBuilder()
                .append(this.entityType, that.entityType)
                .append(this.hash, that.hash)
                .append(this.isErrored, that.isErrored)
                .append(this.latestHandledStatus, that.latestHandledStatus)
                .append(this.latestSubmitStatus, that.latestSubmitStatus)
                .append(this.historyHandledStatus, that.historyHandledStatus)
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("EntityType", entityType)
                .append("Hash", hash)
                .append("isErrored", isErrored)
                .append("latestHandledStatus", latestHandledStatus)
                .append("latestSubmitStatus", latestSubmitStatus)
                .append("historyHandledStatus", historyHandledStatus)
                .toString();
    }

    /**
     * check if the entity's latestHandledStatus is HANDLE_REJECTED
     * @return
     */
    public boolean isHandleRejected() {
        return this.latestHandledStatus != null
                && this.latestHandledStatus.getTransactionState() == TransactionState.HANDLE_REJECTED;
    }
}
