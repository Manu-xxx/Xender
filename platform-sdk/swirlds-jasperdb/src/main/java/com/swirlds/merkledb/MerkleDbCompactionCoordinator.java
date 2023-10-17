/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;
import static java.util.Objects.requireNonNull;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.merkledb.config.MerkleDbConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for coordinating compaction tasks for a {@link MerkleDbDataSource}.
 * It provides convenient API for starting compactions for each of the three storage types. Also, this class makes sure
 * that there are no concurrent compactions for the same storage type. And finally it provides a way to stop all compactions
 * and keep them disabled until they are explicitly enabled again.
 * The compaction tasks are executed in a background thread pool.
 * The number of threads in the pool is defined by {@link MerkleDbConfig#compactionThreads()} property.
 *
 */
class MerkleDbCompactionCoordinator {

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    /**
     * A thread pool to run compaction tasks.
     */
    private static final AtomicReference<ExecutorService> compactionExecutorServiceRef = new AtomicReference<>();

    private static final Logger logger = LogManager.getLogger(MerkleDbCompactionCoordinator.class);

    public static final String HASH_STORE_DISK_SUFFIX = "HashStoreDisk";
    public static final String OBJECT_KEY_TO_PATH_SUFFIX = "ObjectKeyToPath";
    public static final String PATH_TO_KEY_VALUE_SUFFIX = "PathToKeyValue";
    private final AtomicBoolean compactionEnabled = new AtomicBoolean();
    // we need a map of exactly three elements, one per storage
    final ConcurrentMap<String, Future<Boolean>> compactionFuturesByName = new ConcurrentHashMap<>(3);
    private final CompactionTask objectKeyToPathTask;
    private final CompactionTask hashesStoreDiskTask;
    private final CompactionTask pathToKeyValueTask;

    /**
     * Creates a new instance of {@link MerkleDbCompactionCoordinator}.
     * @param tableName the name of the table
     * @param objectKeyToPath an object key to path store
     * @param hashesStoreDisk a hash store
     * @param pathToKeyValue a path to key-value store
     */
    public MerkleDbCompactionCoordinator(
            @NonNull String tableName,
            @Nullable Compactible objectKeyToPath,
            @Nullable Compactible hashesStoreDisk,
            @NonNull Compactible pathToKeyValue) {
        if (objectKeyToPath != null) {
            objectKeyToPathTask = new CompactionTask(tableName + OBJECT_KEY_TO_PATH_SUFFIX, objectKeyToPath);
        } else {
            objectKeyToPathTask = null;
        }
        if (hashesStoreDisk != null) {
            hashesStoreDiskTask = new CompactionTask(tableName + HASH_STORE_DISK_SUFFIX, hashesStoreDisk);
        } else {
            hashesStoreDiskTask = null;
        }
        this.pathToKeyValueTask = new CompactionTask(tableName + PATH_TO_KEY_VALUE_SUFFIX, pathToKeyValue);

        ExecutorService executorService = new ThreadPoolExecutor(
                config.compactionThreads(),
                config.compactionThreads(),
                50L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadConfiguration(getStaticThreadManager())
                        .setThreadGroup(new ThreadGroup("Compaction"))
                        .setComponent(MERKLEDB_COMPONENT)
                        .setThreadName("Compacting")
                        .setExceptionHandler(
                                (t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception during merging", ex))
                        .buildFactory());

        compactionExecutorServiceRef.set(executorService);
    }

    /**
     * Compacts the object key to path store asynchronously if it's present.
     */
    void compactDiskStoreForObjectKeyToPathAsync() {
        if (objectKeyToPathTask == null) {
            return;
        }
        submitCompactionTaskForExecution(objectKeyToPathTask);
    }

    /**
     * Compacts the hash store asynchronously if it's present.
     */
    void compactDiskStoreForHashesAsync() {
        if (hashesStoreDiskTask == null) {
            return;
        }
        submitCompactionTaskForExecution(hashesStoreDiskTask);
    }

    /**
     * Compacts the path to key-value store asynchronously.
     */
    void compactPathToKeyValueAsync() {
        submitCompactionTaskForExecution(pathToKeyValueTask);
    }

    /**
     * Enables background compaction.
     */
    void enableBackgroundCompaction() {
        compactionEnabled.set(true);
    }

    /**
     * Stops all compactions in progress and disables background compaction.
     * All subsequent calls to compacting methods will be ignored until {@link #enableBackgroundCompaction()} is called.
     */
    void stopAndDisableBackgroundCompaction() {
        synchronized (compactionFuturesByName) {
            for (var futureEntry : compactionFuturesByName.values()) {
                futureEntry.cancel(true);
            }
            compactionFuturesByName.clear();
            compactionEnabled.set(false);
        }
    }

    /**
     * Submits a compaction task for execution. If a compaction task for the same storage type is already in progress,
     * the call is effectively no op.
     * @param task a compaction task to execute
     */
    private void submitCompactionTaskForExecution(CompactionTask task) {
        if (!compactionEnabled.get()) {
            return;
        }

        ExecutorService executor = getCompactingExecutor();

        synchronized (compactionFuturesByName) {
            if (compactionFuturesByName.containsKey(task.id)) {
                Future<?> future = compactionFuturesByName.get(task.id);
                if (future.isDone()) {
                    compactionFuturesByName.remove(task.id);
                } else {
                    logger.debug(MERKLE_DB.getMarker(), "Compaction for {} is already in progress", task.id);
                    return;
                }
            }

            compactionFuturesByName.put(task.id, executor.submit(task));
        }
    }

    /**
     * @return a thread pool for compaction tasks
     */
    ExecutorService getCompactingExecutor() {
        return compactionExecutorServiceRef.get();
    }

    boolean isCompactionEnabled() {
        return compactionEnabled.get();
    }

    /**
     * A helper class representing a task to run compaction for a specific storage type.
     */
    private static class CompactionTask implements Callable<Boolean> {

        private static final Logger logger = LogManager.getLogger(CompactionTask.class);
        final String id;
        private final Compactible compactible;

        public CompactionTask(@NonNull String id, @NonNull Compactible compactible) {
            requireNonNull(id);
            requireNonNull(compactible);
            this.id = id;
            this.compactible = compactible;
        }

        @Override
        public Boolean call() {
            try {
                return compactible.compact();
            } catch (final InterruptedException | ClosedByInterruptException e) {
                logger.info(MERKLE_DB.getMarker(), "Interrupted while compacting, this is allowed.", e);
            } catch (Exception e) {
                // It is important that we capture all exceptions here, otherwise a single exception
                // will stop all  future merges from happening.
                logger.error(EXCEPTION.getMarker(), "[{}] Compaction failed", id, e);
            }
            return false;
        }
    }
}
