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

package com.swirlds.platform.state;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.state.MerkleStateUtils.createInfoString;
import static com.swirlds.platform.state.service.PbjConverter.toPbjPlatformState;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.state.StateChangeListener.StateType.MAP;
import static com.swirlds.state.StateChangeListener.StateType.QUEUE;
import static com.swirlds.state.StateChangeListener.StateType.SINGLETON;
import static com.swirlds.state.merkle.StateUtils.computeLabel;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.utility.Labeled;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileWriter;
import com.swirlds.platform.state.snapshot.StateToDiskReason;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.merkle.disk.OnDiskReadableKVState;
import com.swirlds.state.merkle.disk.OnDiskWritableKVState;
import com.swirlds.state.merkle.memory.InMemoryReadableKVState;
import com.swirlds.state.merkle.memory.InMemoryWritableKVState;
import com.swirlds.state.merkle.queue.QueueNode;
import com.swirlds.state.merkle.queue.ReadableQueueStateImpl;
import com.swirlds.state.merkle.queue.WritableQueueStateImpl;
import com.swirlds.state.merkle.singleton.ReadableSingletonStateImpl;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.merkle.singleton.WritableSingletonStateImpl;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.EmptyReadableStates;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.QueueChangeListener;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link SwirldState} and {@link State}. The Hashgraph Platform
 * communicates with the application through {@link SwirldMain} and {@link
 * SwirldState}. The Hedera application, after startup, only needs the ability to get {@link
 * ReadableStates} and {@link WritableStates} from this object.
 *
 * <p>Among {@link MerkleStateRoot}'s child nodes are the various {@link
 * com.swirlds.merkle.map.MerkleMap}'s and {@link com.swirlds.virtualmap.VirtualMap}'s that make up
 * the service's states. Each such child node has a label specified that is computed from the
 * metadata for that state. Since both service names and state keys are restricted to characters
 * that do not include the period, we can use it to separate service name from state key. When we
 * need to find all states for a service, we can do so by iteration and string comparison.
 *
 * <p>NOTE: The implementation of this class must change before we can support state proofs
 * properly. In particular, a wide n-ary number of children is less than ideal, since the hash of
 * each child must be part of the state proof. It would be better to have a binary tree. We should
 * consider nesting service nodes in a MerkleMap, or some other such approach to get a binary tree.
 */
@ConstructableIgnored
public class MerkleStateRoot extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState, State, MerkleRoot {

    private static final Logger logger = LogManager.getLogger(MerkleStateRoot.class);

    /**
     * Used when asked for a service's readable states that we don't have
     */
    private static final ReadableStates EMPTY_READABLE_STATES = new EmptyReadableStates();

    private static final long CLASS_ID = 0x8e300b0dfdafbb1aL;
    // Migrates from `PlatformState` to State API singleton
    public static final int CURRENT_VERSION = 31;

    // This is a temporary fix to deal with the inefficient implementation of findNodeIndex(). It caches looked up
    // indices globally, assuming these indices do not change that often. We need to re-think index lookup,
    // but at this point all major rewrites seem to risky.
    private static final Map<String, Integer> INDEX_LOOKUP = new ConcurrentHashMap<>();

    /**
     * The callbacks for Hedera lifecycle events.
     */
    private final MerkleStateLifecycles lifecycles;

    private final Function<SemanticVersion, SoftwareVersion> versionFactory;
    private MerkleCryptography merkleCryptography;
    private PlatformContext platformContext;
    private Platform platform;

    public Map<String, Map<String, StateMetadata<?, ?>>> getServices() {
        return services;
    }

    private Metrics metrics;

    /**
     * Maintains information about each service, and each state of each service, known by this
     * instance. The key is the "service-name.state-key".
     */
    private final Map<String, Map<String, StateMetadata<?, ?>>> services = new HashMap<>();

    /**
     * Cache of used {@link ReadableStates}.
     */
    private final Map<String, ReadableStates> readableStatesMap = new ConcurrentHashMap<>();

    /**
     * Cache of used {@link WritableStates}.
     */
    private final Map<String, MerkleWritableStates> writableStatesMap = new HashMap<>();
    /**
     * Listeners to be notified of state changes on {@link MerkleWritableStates#commit()} calls for any service.
     */
    private final List<StateChangeListener> listeners = new ArrayList<>();

    /**
     * Once set, the possibly empty list of builders for state changes that occurred when initializing
     * the platform state.
     */
    @Nullable
    private List<StateChanges.Builder> platformStateInitChanges;

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    /**
     * If set, the platform state from a deserialized state created prior to version 0.54, and used to initialize
     * the platform state as a State API singleton after migration.
     *
     * @deprecated since 0.54.0; this field should be removed in a future release
     */
    @Nullable
    @Deprecated(forRemoval = true)
    private PlatformState preV054PlatformState;

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     * @param lifecycles            The lifecycle callbacks. Cannot be null.
     * @param versionFactory a factory for creating {@link SoftwareVersion} based on provided {@link SemanticVersion}
     */
    public MerkleStateRoot(
            @NonNull final MerkleStateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        this.lifecycles = requireNonNull(lifecycles);
        this.registryRecord = RuntimeObjectRegistry.createRecord(getClass());
        this.versionFactory = requireNonNull(versionFactory);
    }

    /**
     * Returns the platform state found at child index 0 of a pre-0.54 state, or null if not found.
     * @return the pre-0.54 platform state, or null if not found
     */
    public @Nullable PlatformState getPreV054PlatformState() {
        return preV054PlatformState;
    }

    /**
     * Returns the list of builders for state changes that occurred during the initialization of the platform state.
     * Must only be called after platform state is initialized.
     * @throws NullPointerException if the platform state initialization changes have not been set
     */
    public @NonNull List<StateChanges.Builder> platformStateInitChangesOrThrow() {
        return requireNonNull(platformStateInitChanges);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Called by the platform whenever the state should be initialized. This can happen at genesis startup,
     * on restart, on reconnect, or any other time indicated by the {@code trigger}.
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion deserializedVersion) {
        this.platform = platform;
        platformContext = this.platform.getContext();
        metrics = platformContext.getMetrics();
        merkleCryptography = platformContext.getMerkleCryptography();

        // If we are initialized for event stream recovery, we have to register an
        // extra listener to make sure we call all the required Hedera lifecycles
        if (trigger == EVENT_STREAM_RECOVERY) {
            final var notificationEngine = platform.getNotificationEngine();
            notificationEngine.register(
                    NewRecoveredStateListener.class,
                    notification -> lifecycles.onNewRecoveredState(notification.getSwirldState()));
        }
        // At some point this method will no longer be defined on SwirldState2, because we want to move
        // to a model where SwirldState/SwirldState2 are simply data objects, without this lifecycle.
        // Instead, this method will be a callback the app registers with the platform. So for now,
        // we simply call the callback handler, which is implemented by the app.
        lifecycles.onStateInitialized(this, platform, trigger, deserializedVersion);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public AddressBook updateWeight(
            @NonNull final AddressBook configAddressBook, @NonNull final PlatformContext context) {
        lifecycles.onUpdateWeight(this, configAddressBook, context);
        return configAddressBook;
    }

    /**
     * Protected constructor for fast-copy.
     *
     * @param from The other state to fast-copy from. Cannot be null.
     */
    protected MerkleStateRoot(@NonNull final MerkleStateRoot from) {
        // Copy the Merkle route from the source instance
        super(from);

        this.lifecycles = from.lifecycles;
        this.registryRecord = RuntimeObjectRegistry.createRecord(getClass());
        this.versionFactory = from.versionFactory;
        this.preV054PlatformState = from.preV054PlatformState;
        this.platformStateInitChanges = from.platformStateInitChanges;
        this.listeners.addAll(from.listeners);

        // Copy over the metadata
        for (final var entry : from.services.entrySet()) {
            this.services.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        // Note we don't "compress" -- null children remain in here unless we manually remove them
        // (which would cause massive re-hashing).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    /**
     * To be called ONLY at node shutdown. Attempts to gracefully close any virtual maps. This method is a bit of a
     * hack, ideally there would be something more generic at the platform level that virtual maps could hook into
     * to get shutdown in an orderly way.
     */
    public void close() {
        logger.info("Closing MerkleStateRoot");
        for (final var svc : services.values()) {
            for (final var md : svc.values()) {
                final var index = findNodeIndex(md.serviceName(), extractStateKey(md));
                if (index >= 0) {
                    final var node = getChild(index);
                    if (node instanceof VirtualMap<?, ?> virtualMap) {
                        try {
                            virtualMap.getDataSource().close();
                        } catch (IOException e) {
                            logger.warn("Unable to close data source for virtual map {}", md.serviceName(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroyNode() {
        registryRecord.release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return readableStatesMap.computeIfAbsent(serviceName, s -> {
            final var stateMetadata = services.get(s);
            return stateMetadata == null ? EMPTY_READABLE_STATES : new MerkleReadableStates(stateMetadata);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        throwIfImmutable();
        return writableStatesMap.computeIfAbsent(serviceName, s -> {
            final var stateMetadata = services.getOrDefault(s, Map.of());
            return new MerkleWritableStates(serviceName, stateMetadata);
        });
    }

    @Override
    public void registerCommitListener(@NonNull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    public void unregisterCommitListener(@NonNull final StateChangeListener listener) {
        requireNonNull(listener);
        listeners.remove(listener);
    }
    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MerkleStateRoot copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new MerkleStateRoot(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final PlatformStateAccessor platformState) {
        throwIfImmutable();
        lifecycles.onHandleConsensusRound(round, this);
    }

    @Override
    public void sealConsensusRound(@NonNull final Round round) {
        requireNonNull(round);
        throwIfImmutable();
        lifecycles.onSealConsensusRound(round, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(@NonNull final Event event) {
        lifecycles.onPreHandle(event, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(final int version) {

        if (version < CURRENT_VERSION) {
            PlatformState platformState = null;
            int platformStateIndex = -1;
            for (int i = 0; i < getNumberOfChildren(); i++) {
                if (getChild(i) instanceof PlatformState ps) {
                    platformState = ps;
                    platformStateIndex = i;
                    break;
                }
            }
            if (platformState == null) {
                throw new IllegalStateException("Expected MerkleStateRoot to have PlatformState as a child");
            }

            preV054PlatformState = platformState;
            logger.info(STARTUP.getMarker(), "Found pre-0.54 PlatformState, will migrate to State API singleton");
            INDEX_LOOKUP.clear();
            final List<MerkleNode> newChildren = new ArrayList<>();
            for (int i = 0, n = getNumberOfChildren(); i < n; i++) {
                // Skip the platform state, it will be added later
                if (i == platformStateIndex) {
                    continue;
                }
                final var child = getChild(i);
                if (child != null) {
                    newChildren.add(child.copy());
                }
            }
            addDeserializedChildren(newChildren, CURRENT_VERSION);
        }
        // Always return this node, we never want to replace MerkleStateRoot node in the tree
        return this;
    }

    /**
     * Puts the defined service state and its associated node into the merkle tree. The precondition
     * for calling this method is that node MUST be a {@link MerkleMap} or {@link VirtualMap} and
     * MUST have a correct label applied. If the node is already present, then this method does nothing
     * else.
     *
     * @param md The metadata associated with the state
     * @param nodeSupplier Returns the node to add. Cannot be null. Can be used to create the node on-the-fly.
     * @throws IllegalArgumentException if the node is neither a merkle map nor virtual map, or if
     * it doesn't have a label, or if the label isn't right.
     */
    public void putServiceStateIfAbsent(
            @NonNull final StateMetadata<?, ?> md, @NonNull final Supplier<? extends MerkleNode> nodeSupplier) {
        putServiceStateIfAbsent(md, nodeSupplier, n -> {});
    }

    /**
     * Puts the defined service state and its associated node into the merkle tree. The precondition
     * for calling this method is that node MUST be a {@link MerkleMap} or {@link VirtualMap} and
     * MUST have a correct label applied. No matter if the resulting node is newly created or already
     * present, calls the provided initialization consumer with the node.
     *
     * @param md The metadata associated with the state
     * @param nodeSupplier Returns the node to add. Cannot be null. Can be used to create the node on-the-fly.
     * @param nodeInitializer The node's initialization logic.
     * @throws IllegalArgumentException if the node is neither a merkle map nor virtual map, or if
     * it doesn't have a label, or if the label isn't right.
     */
    public <T extends MerkleNode> void putServiceStateIfAbsent(
            @NonNull final StateMetadata<?, ?> md,
            @NonNull final Supplier<T> nodeSupplier,
            @NonNull final Consumer<T> nodeInitializer) {

        // Validate the inputs
        throwIfImmutable();
        requireNonNull(md);
        requireNonNull(nodeSupplier);
        requireNonNull(nodeInitializer);

        // Put this metadata into the map
        final var def = md.stateDefinition();
        final var serviceName = md.serviceName();
        final var stateMetadata = services.computeIfAbsent(serviceName, k -> new HashMap<>());
        stateMetadata.put(def.stateKey(), md);

        // We also need to add/update the metadata of the service in the writableStatesMap so that
        // it isn't stale or incomplete (e.g. in a genesis case)
        readableStatesMap.put(serviceName, new MerkleReadableStates(stateMetadata));
        writableStatesMap.put(serviceName, new MerkleWritableStates(serviceName, stateMetadata));

        // Look for a node, and if we don't find it, then insert the one we were given
        // If there is not a node there, then set it. I don't want to overwrite the existing node,
        // because it may have been loaded from state on disk, and the node provided here in this
        // call is always for genesis. So we may just ignore it.
        final T node;
        final var nodeIndex = findNodeIndex(serviceName, def.stateKey());
        if (nodeIndex == -1) {
            node = requireNonNull(nodeSupplier.get());
            final var label = node instanceof Labeled labeled ? labeled.getLabel() : null;
            if (label == null) {
                throw new IllegalArgumentException("`node` must be a Labeled and have a label");
            }

            if (def.onDisk() && !(node instanceof VirtualMap<?, ?>)) {
                throw new IllegalArgumentException(
                        "Mismatch: state definition claims on-disk, but " + "the merkle node is not a VirtualMap");
            }

            if (label.isEmpty()) {
                // It looks like both MerkleMap and VirtualMap do not allow for a null label.
                // But I want to leave this check in here anyway, in case that is ever changed.
                throw new IllegalArgumentException("A label must be specified on the node");
            }

            if (!label.equals(computeLabel(serviceName, def.stateKey()))) {
                throw new IllegalArgumentException(
                        "A label must be computed based on the same " + "service name and state key in the metadata!");
            }

            setChild(getNumberOfChildren(), node);
        } else {
            node = getChild(nodeIndex);
        }
        nodeInitializer.accept(node);
    }

    /**
     * Removes the node and metadata from the state merkle tree.
     *
     * @param serviceName The service name. Cannot be null.
     * @param stateKey The state key
     */
    public void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {
        throwIfImmutable();
        requireNonNull(serviceName);
        requireNonNull(stateKey);

        // Remove the metadata entry
        final var stateMetadata = services.get(serviceName);
        if (stateMetadata != null) {
            stateMetadata.remove(stateKey);
        }

        // Eventually remove the cached WritableState
        final var writableStates = writableStatesMap.get(serviceName);
        if (writableStates != null) {
            writableStates.remove(stateKey);
        }

        // Remove the node
        final var index = findNodeIndex(serviceName, stateKey);
        if (index != -1) {
            setChild(index, null);
        }
    }

    /**
     * Simple utility method that finds the state node index.
     *
     * @param serviceName the service name
     * @param stateKey the state key
     * @return -1 if not found, otherwise the index into the children
     */
    public int findNodeIndex(@NonNull final String serviceName, @NonNull final String stateKey) {
        final var label = computeLabel(serviceName, stateKey);

        final Integer index = INDEX_LOOKUP.get(label);
        if (index != null && checkNodeIndex(index, label)) {
            return index;
        }

        for (int i = 0, n = getNumberOfChildren(); i < n; i++) {
            if (checkNodeIndex(i, label)) {
                INDEX_LOOKUP.put(label, i);
                return i;
            }
        }

        INDEX_LOOKUP.remove(label);
        return -1;
    }

    private boolean checkNodeIndex(final int index, @NonNull final String label) {
        final var node = getChild(index);
        return node instanceof Labeled labeled && Objects.equals(label, labeled.getLabel());
    }

    /**
     * Base class implementation for states based on MerkleTree
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private abstract class MerkleStates implements ReadableStates {
        protected final Map<String, StateMetadata<?, ?>> stateMetadata;
        protected final Map<String, ReadableKVState<?, ?>> kvInstances;
        protected final Map<String, ReadableSingletonState<?>> singletonInstances;
        protected final Map<String, ReadableQueueState<?>> queueInstances;
        private final Set<String> stateKeys;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            this.stateMetadata = requireNonNull(stateMetadata);
            this.stateKeys = Collections.unmodifiableSet(stateMetadata.keySet());
            this.kvInstances = new HashMap<>();
            this.singletonInstances = new HashMap<>();
            this.queueInstances = new HashMap<>();
        }

        @NonNull
        @Override
        public <K, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
            final ReadableKVState<K, V> instance = (ReadableKVState<K, V>) kvInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || md.stateDefinition().singleton()) {
                throw new IllegalArgumentException("Unknown k/v state key '" + stateKey + ";");
            }

            final var node = findNode(md);
            if (node instanceof VirtualMap v) {
                final var ret = createReadableKVState(md, v);
                kvInstances.put(stateKey, ret);
                return ret;
            } else if (node instanceof MerkleMap m) {
                final var ret = createReadableKVState(md, m);
                kvInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for k/v state " + stateKey);
            }
        }

        @NonNull
        @Override
        public <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey) {
            final ReadableSingletonState<T> instance = (ReadableSingletonState<T>) singletonInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || !md.stateDefinition().singleton()) {
                throw new IllegalArgumentException("Unknown singleton state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof SingletonNode s) {
                final var ret = createReadableSingletonState(md, s);
                singletonInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for singleton state " + stateKey);
            }
        }

        @NonNull
        @Override
        public <E> ReadableQueueState<E> getQueue(@NonNull String stateKey) {
            final ReadableQueueState<E> instance = (ReadableQueueState<E>) queueInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || !md.stateDefinition().queue()) {
                throw new IllegalArgumentException("Unknown queue state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof QueueNode q) {
                final var ret = createReadableQueueState(md, q);
                queueInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for queue state " + stateKey);
            }
        }

        @Override
        public boolean contains(@NonNull final String stateKey) {
            return stateMetadata.containsKey(stateKey);
        }

        @NonNull
        @Override
        public Set<String> stateKeys() {
            return stateKeys;
        }

        @NonNull
        protected abstract ReadableKVState createReadableKVState(@NonNull StateMetadata md, @NonNull VirtualMap v);

        @NonNull
        protected abstract ReadableKVState createReadableKVState(@NonNull StateMetadata md, @NonNull MerkleMap m);

        @NonNull
        protected abstract ReadableSingletonState createReadableSingletonState(
                @NonNull StateMetadata md, @NonNull SingletonNode<?> s);

        @NonNull
        protected abstract ReadableQueueState createReadableQueueState(
                @NonNull StateMetadata md, @NonNull QueueNode<?> q);

        /**
         * Utility method for finding and returning the given node. Will throw an ISE if such a node
         * cannot be found!
         *
         * @param md The metadata
         * @return The found node
         */
        @NonNull
        MerkleNode findNode(@NonNull final StateMetadata<?, ?> md) {
            final var index = findNodeIndex(md.serviceName(), extractStateKey(md));
            if (index == -1) {
                // This can only happen if there WAS a node here, and it was removed!
                throw new IllegalStateException("State '"
                        + extractStateKey(md)
                        + "' for service '"
                        + md.serviceName()
                        + "' is missing from the merkle tree!");
            }

            return getChild(index);
        }
    }

    /**
     * An implementation of {@link ReadableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final class MerkleReadableStates extends MerkleStates {
        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleReadableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            return new OnDiskReadableKVState<>(
                    extractStateKey(md),
                    md.onDiskKeyClassId(),
                    md.stateDefinition().keyCodec(),
                    v);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            return new InMemoryReadableKVState<>(extractStateKey(md), m);
        }

        @Override
        @NonNull
        protected ReadableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            return new ReadableSingletonStateImpl<>(extractStateKey(md), s);
        }

        @NonNull
        @Override
        protected ReadableQueueState createReadableQueueState(@NonNull StateMetadata md, @NonNull QueueNode<?> q) {
            return new ReadableQueueStateImpl(extractStateKey(md), q);
        }
    }

    /**
     * An implementation of {@link WritableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final class MerkleWritableStates extends MerkleStates implements WritableStates, CommittableWritableStates {

        private final String serviceName;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleWritableStates(
                @NonNull final String serviceName, @NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
            this.serviceName = requireNonNull(serviceName);
        }

        /**
         * Copies and releases the {@link VirtualMap} for the given state key. This ensures
         * data is continually flushed to disk
         *
         * @param stateKey the state key
         */
        public void copyAndReleaseVirtualMap(@NonNull final String stateKey) {
            final var md = stateMetadata.get(stateKey);
            final VirtualMap<?, ?> virtualMap = (VirtualMap<?, ?>) findNode(md);
            final var mutableCopy = virtualMap.copy();
            if (metrics != null) {
                mutableCopy.registerMetrics(metrics);
            }
            setChild(findNodeIndex(serviceName, stateKey), mutableCopy);
            kvInstances.put(stateKey, createReadableKVState(md, mutableCopy));
        }

        @NonNull
        @Override
        public <K, V> WritableKVState<K, V> get(@NonNull String stateKey) {
            return (WritableKVState<K, V>) super.get(stateKey);
        }

        @NonNull
        @Override
        public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
            return (WritableSingletonState<T>) super.getSingleton(stateKey);
        }

        @NonNull
        @Override
        public <E> WritableQueueState<E> getQueue(@NonNull String stateKey) {
            return (WritableQueueState<E>) super.getQueue(stateKey);
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            final var state = new OnDiskWritableKVState<>(
                    extractStateKey(md),
                    md.onDiskKeyClassId(),
                    md.stateDefinition().keyCodec(),
                    md.onDiskValueClassId(),
                    md.stateDefinition().valueCodec(),
                    v);
            listeners.forEach(listener -> {
                if (listener.stateTypes().contains(MAP)) {
                    registerKVListener(serviceName, state, listener);
                }
            });
            return state;
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            final var state = new InMemoryWritableKVState<>(
                    extractStateKey(md),
                    md.inMemoryValueClassId(),
                    md.stateDefinition().keyCodec(),
                    md.stateDefinition().valueCodec(),
                    m);
            listeners.forEach(listener -> {
                if (listener.stateTypes().contains(MAP)) {
                    registerKVListener(serviceName, state, listener);
                }
            });
            return state;
        }

        @Override
        @NonNull
        protected WritableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            final var state = new WritableSingletonStateImpl<>(extractStateKey(md), s);
            listeners.forEach(listener -> {
                if (listener.stateTypes().contains(SINGLETON)) {
                    registerSingletonListener(serviceName, state, listener);
                }
            });
            return state;
        }

        @NonNull
        @Override
        protected WritableQueueState<?> createReadableQueueState(
                @NonNull final StateMetadata md, @NonNull final QueueNode<?> q) {
            final var state = new WritableQueueStateImpl<>(extractStateKey(md), q);
            listeners.forEach(listener -> {
                if (listener.stateTypes().contains(QUEUE)) {
                    registerQueueListener(serviceName, state, listener);
                }
            });
            return state;
        }

        @Override
        public void commit() {
            for (final ReadableKVState kv : kvInstances.values()) {
                ((WritableKVStateBase) kv).commit();
            }
            for (final ReadableSingletonState s : singletonInstances.values()) {
                ((WritableSingletonStateBase) s).commit();
            }
            for (final ReadableQueueState q : queueInstances.values()) {
                ((WritableQueueStateBase) q).commit();
            }
            readableStatesMap.remove(serviceName);
        }

        /**
         * This method is called when a state is removed from the state merkle tree. It is used to
         * remove the cached instances of the state.
         *
         * @param stateKey the state key
         */
        public void remove(String stateKey) {
            stateMetadata.remove(stateKey);
            kvInstances.remove(stateKey);
            singletonInstances.remove(stateKey);
            queueInstances.remove(stateKey);
        }

        private <V> void registerSingletonListener(
                @NonNull final String serviceName,
                @NonNull final WritableSingletonStateBase<V> singletonState,
                @NonNull final StateChangeListener listener) {
            final var stateId = listener.stateIdFor(serviceName, singletonState.getStateKey());
            singletonState.registerListener(value -> listener.singletonUpdateChange(stateId, value));
        }

        private <V> void registerQueueListener(
                @NonNull final String serviceName,
                @NonNull final WritableQueueStateBase<V> queueState,
                @NonNull final StateChangeListener listener) {
            final var stateId = listener.stateIdFor(serviceName, queueState.getStateKey());
            queueState.registerListener(new QueueChangeListener<>() {
                @Override
                public void queuePushChange(@NonNull final V value) {
                    listener.queuePushChange(stateId, value);
                }

                @Override
                public void queuePopChange() {
                    listener.queuePopChange(stateId);
                }
            });
        }

        private <K, V> void registerKVListener(
                @NonNull final String serviceName, WritableKVStateBase<K, V> state, StateChangeListener listener) {
            final var stateId = listener.stateIdFor(serviceName, state.getStateKey());
            state.registerListener(new KVChangeListener<>() {
                @Override
                public void mapUpdateChange(@NonNull final K key, @NonNull final V value) {
                    listener.mapUpdateChange(stateId, key, value);
                }

                @Override
                public void mapDeleteChange(@NonNull final K key) {
                    listener.mapDeleteChange(stateId, key);
                }
            });
        }
    }

    /**
     * Returns a factory constructing instances of {@link SoftwareVersion} based on provided {@link SemanticVersion}.
     */
    @NonNull
    public Function<SemanticVersion, SoftwareVersion> getVersionFactory() {
        return versionFactory;
    }

    @NonNull
    private static String extractStateKey(@NonNull final StateMetadata<?, ?> md) {
        return md.stateDefinition().stateKey();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SwirldState getSwirldState() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStateAccessor getReadablePlatformState() {
        return readablePlatformStateStore();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStateAccessor getWritablePlatformState() {
        if (isImmutable()) {
            throw new IllegalStateException("Cannot get writable platform state when state is immutable");
        }
        return writablePlatformStateStore();
    }

    /**
     * Updates the platform state with the values from the provided instance of {@link PlatformStateAccessor}
     *
     * @param accessor a source of values
     */
    @Override
    public void updatePlatformState(@NonNull final PlatformStateAccessor accessor) {
        writablePlatformStateStore().setAllFrom(accessor);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getInfoString(final int hashDepth) {
        return createInfoString(hashDepth, readablePlatformStateStore(), getHash(), this);
    }

    private ReadablePlatformStateStore readablePlatformStateStore() {
        return new ReadablePlatformStateStore(getReadableStates(PlatformStateService.NAME), versionFactory);
    }

    private WritablePlatformStateStore writablePlatformStateStore() {
        if (!services.containsKey(PlatformStateService.NAME)) {
            platformStateInitChanges = lifecycles.initPlatformState(this);
        }
        final var store = new WritablePlatformStateStore(getWritableStates(PlatformStateService.NAME), versionFactory);
        if (preV054PlatformState != null) {
            store.setAllFrom(preV054PlatformState);
            logger.info(
                    STARTUP.getMarker(),
                    "Migrated PlatformState {} to State API singleton",
                    toPbjPlatformState(preV054PlatformState));
            preV054PlatformState = null;
        }
        return store;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void computeHash() {
        requireNonNull(
                merkleCryptography,
                "MerkleStateRoot has to be initialized before hashing. merkleCryptography is not set.");
        throwIfMutable("Hashing should only be done on immutable states");
        throwIfDestroyed("Hashing should not be done on destroyed states");
        if (getHash() != null) {
            return;
        }
        try {
            merkleCryptography.digestTreeAsync(this).get();
        } catch (final ExecutionException e) {
            logger.error(EXCEPTION.getMarker(), "Exception occurred during hashing", e);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Interrupted while hashing state. Expect buggy behavior.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshot(Path targetPath) {
        throwIfMutable();
        throwIfDestroyed();
        final SignedState signedState = new SignedState(
                platformContext, CryptoStatic::verifySignature, this, "MerkleStateRoot.snapshot()", true, true, false);
        signedState.markAsStateToSave(StateToDiskReason.SNAPSHOT_ON_DEMAND);
        try {
            SignedStateFileWriter.writeSignedStateToDisk(
                    platformContext, platform.getSelfId(), targetPath, signedState, signedState.getStateToDiskReason());
        } catch (final Throwable e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to write signed state to disk for round {} to {}.",
                    signedState.getRound(),
                    targetPath,
                    e);
        }
    }
}
