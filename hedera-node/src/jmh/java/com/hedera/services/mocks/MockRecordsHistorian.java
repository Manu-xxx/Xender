package com.hedera.services.mocks;

import com.hedera.services.records.InProgressChildRecord;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.swirlds.common.crypto.RunningHash;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MockRecordsHistorian implements RecordsHistorian {
    @Inject
    public MockRecordsHistorian() {
        // Dagger2
    }

    @Override
    public void clearHistory() {
        // No-op
    }

    @Override
    public void setCreator(EntityCreator creator) {
        // No-op
    }

    @Override
    public void saveExpirableTransactionRecords() {
        // No-op
    }

    @Override
    public RecordStreamObject getTopLevelRecord() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasFollowingChildRecords() {
        return false;
    }

    @Override
    public boolean hasPrecedingChildRecords() {
        return false;
    }

    @Override
    public List<RecordStreamObject> getFollowingChildRecords() {
        return List.of();
    }

    @Override
    public List<RecordStreamObject> getPrecedingChildRecords() {
        return List.of();
    }

    @Override
    public int nextChildRecordSourceId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trackFollowingChildRecord(int sourceId, Builder syntheticBody,
        ExpirableTxnRecord.Builder recordSoFar, List<TransactionSidecarRecord.Builder> sidecars) {
        // No-op
    }

    @Override
    public void trackPrecedingChildRecord(
            int sourceId, Builder syntheticBody, ExpirableTxnRecord.Builder recordSoFar) {
        // No-op
    }

    @Override
    public void revertChildRecordsFromSource(int sourceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void noteNewExpirationEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant nextFollowingChildConsensusTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void customizeSuccessor(
            Predicate<InProgressChildRecord> matcher, Consumer<InProgressChildRecord> customizer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RunningHash lastRunningHash() {
        throw new UnsupportedOperationException();
    }
}
