/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.CommonUtils.combineConsumers;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.GenesisStateBuilder.buildGenesisState;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;

import com.swirlds.base.state.Startable;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.threading.SyncPermitProvider;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.utility.SequenceCycle;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.utility.LoggingClearables;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.logging.LogMarker;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventCreator;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.EventTaskDispatcher;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PostConsensusSystemTransactionManagerFactory;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreConsensusSystemTransactionManagerFactory;
import com.swirlds.platform.components.wiring.ManualWiring;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.event.EventCreatorThread;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.event.creation.BelowIntCreationRule;
import com.swirlds.platform.event.creation.ChatterEventCreator;
import com.swirlds.platform.event.creation.ChatteringRule;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.event.preconsensus.AsyncPreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.NoOpPreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreConsensusEventStreamConfig;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamSequencer;
import com.swirlds.platform.event.preconsensus.SyncPreConsensusEventWriter;
import com.swirlds.platform.event.validation.AncientValidator;
import com.swirlds.platform.event.validation.EventDeduplication;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.SignatureValidator;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.gossip.FallenBehindManagerImpl;
import com.swirlds.platform.gossip.chatter.ChatterNotifier;
import com.swirlds.platform.gossip.chatter.ChatterSyncProtocol;
import com.swirlds.platform.gossip.chatter.PrepareChatterEvent;
import com.swirlds.platform.gossip.chatter.communication.ChatterProtocol;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.shadowgraph.SimultaneousSyncThrottle;
import com.swirlds.platform.gossip.shadowgraph.SingleNodeNetworkSync;
import com.swirlds.platform.gossip.sync.SyncCaller;
import com.swirlds.platform.gossip.sync.SyncManagerImpl;
import com.swirlds.platform.gossip.sync.SyncProtocolResponder;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.heartbeats.HeartbeatProtocol;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.NetworkStatsTransmitter;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.network.unidirectional.HeartbeatProtocolResponder;
import com.swirlds.platform.network.unidirectional.HeartbeatSender;
import com.swirlds.platform.network.unidirectional.Listener;
import com.swirlds.platform.network.unidirectional.MultiProtocolResponder;
import com.swirlds.platform.network.unidirectional.ProtocolMapping;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.network.unidirectional.UnidirectionalProtocols;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectProtocolResponder;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import com.swirlds.platform.threading.PauseAndClear;
import com.swirlds.platform.threading.PauseAndLoad;
import com.swirlds.platform.util.PlatformComponents;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwirldsPlatform implements Platform, PlatformWithDeprecatedMethods, ConnectionTracker, Startable {

    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SwirldsPlatform.class);
    /** alert threshold for java app pause */
    private static final long PAUSE_ALERT_INTERVAL = 5000;
    /** logging string prefix for hash stream operation logged events. */
    private static final String HASH_STREAM_OPERATION_PREFIX = ">>> ";
    /**
     * the ID of the member running this. Since a node can be a main node or a mirror node, the ID is not a primitive
     * value
     */
    private final NodeId selfId;
    /** tell which pairs of members should establish connections */
    private final NetworkTopology topology;
    /**
     * This object is responsible for rate limiting reconnect attempts (in the role of sender)
     */
    private final ReconnectThrottle reconnectThrottle;

    private final Settings settings = Settings.getInstance();
    /** A type used by Hashgraph, Statistics, and SyncUtils. Only Hashgraph modifies this type instance. */
    private final EventMapper eventMapper;
    /**
     * A simpler event mapper used for chatter. Stores the thread-safe GossipEvent and has less functionality. The plan
     * is for this to replace EventMapper once syncing is removed from the code
     */
    private final ChatterEventMapper chatterEventMapper;
    /** this is the Nth Platform running on this machine (N=winNum) */
    private final int instanceNumber;
    /** parameters given to the app when it starts */
    private final String[] parameters;
    /** Handles all system transactions pre-consensus */
    private final PreConsensusSystemTransactionManager preConsensusSystemTransactionManager;
    /** Handles all system transactions post-consensus */
    private final PostConsensusSystemTransactionManager postConsensusSystemTransactionManager;
    /** The platforms freeze manager */
    private final FreezeManager freezeManager;
    /** is used for pausing event creation for a while at start up */
    private final StartUpEventFrozenManager startUpEventFrozenManager;
    /**
     * The shadow graph manager. This wraps a shadow graph, which is an Event graph that adds child pointers to the
     * Hashgraph Event graph. Used for gossiping.
     */
    private final ShadowGraph shadowGraph;
    /** The last status of the platform that was determined, is null until the platform starts up */
    private final AtomicReference<PlatformStatus> currentPlatformStatus = new AtomicReference<>(null);
    /** the number of active connections this node has to other nodes */
    private final AtomicInteger activeConnectionNumber = new AtomicInteger(0);
    /**
     * the object used to calculate consensus. it is volatile because the whole object is replaced when reading a state
     * from disk or getting it through reconnect
     */
    private final AtomicReference<Consensus> consensusRef;
    /** set in the constructor and given to the SwirldState object in run() */
    private final AddressBook initialAddressBook;

    private final Metrics metrics;
    private final PlatformMetrics platformMetrics;

    private final SimultaneousSyncThrottle simultaneousSyncThrottle;
    private final ConsensusMetrics consensusMetrics;
    private final EventIntakeMetrics eventIntakeMetrics;
    private final SyncMetrics syncMetrics;
    private final NetworkMetrics networkMetrics;
    private final ReconnectMetrics reconnectMetrics;
    /** Reference to the instance responsible for executing reconnect when chatter is used */
    private final AtomicReference<ReconnectController> reconnectController = new AtomicReference<>();
    /** tracks if we have fallen behind or not, takes appropriate action if we have */
    private final FallenBehindManagerImpl fallenBehindManager;

    private final ChatterCore<GossipEvent> chatterCore;
    /** all the events and other data about the hashgraph */
    private EventTaskCreator eventTaskCreator;
    /** ID number of the swirld being run */
    private final byte[] swirldId;
    /** the object that contains all key pairs and CSPRNG state for this member */
    private final Crypto crypto;
    /** a long name including (app, swirld, member id, member self name) */
    private final String platformName;
    /** is used for calculating runningHash of all consensus events and writing consensus events to file */
    private final EventStreamManager<EventImpl> eventStreamManager;
    /**
     * True if this node started from genesis.
     */
    private boolean startedFromGenesis;
    /**
     * If a state was loaded from disk, this will have the round of that state.
     */
    private long diskStateRound;
    /**
     * If a state was loaded from disk, this will have the hash of that state.
     */
    private Hash diskStateHash;
    /** Helps when executing a reconnect */
    private ReconnectHelper reconnectHelper;
    /** tells callers who to sync with and keeps track of whether we have fallen behind */
    private SyncManagerImpl syncManager;
    /** locks used to synchronize usage of outbound connections */
    private SharedConnectionLocks sharedConnectionLocks;

    private final StateManagementComponent stateManagementComponent;
    /** last time stamp when pause check timer is active */
    private long pauseCheckTimeStamp;
    /** Tracks recent events created in the network */
    private final CriticalQuorum criticalQuorum;

    private final QueueThread<EventIntakeTask> intakeQueue;
    private final EventLinker eventLinker;
    private SequenceCycle<EventIntakeTask> intakeCycle = null;
    /** sleep in ms after each sync in SyncCaller. A public setter for this exists. */
    private long delayAfterSync = 0;
    /**
     * Executes a sync with a remote node. Only used for sync, not chatter.
     */
    private ShadowGraphSynchronizer syncShadowgraphSynchronizer;
    /** Stores and passes pre-consensus events to {@link SwirldStateManager} for handling */
    private final PreConsensusEventHandler preConsensusEventHandler;
    /** Stores and processes consensus events including sending them to {@link SwirldStateManager} for handling */
    private final ConsensusRoundHandler consensusRoundHandler;
    /** Handles all interaction with {@link SwirldState} */
    private final SwirldStateManager swirldStateManager;
    /** Checks the validity of transactions and submits valid ones to the event transaction pool */
    private final SwirldTransactionSubmitter transactionSubmitter;
    /** clears all pipelines to prepare for a reconnect */
    private Clearable clearAllPipelines;
    /** Contains all information and state required for emergency recovery */
    private final EmergencyRecoveryManager emergencyRecoveryManager;

    /**
     * Responsible for managing the lifecycle of threads on this platform.
     */
    private final ThreadManager threadManager;

    /**
     * A list of threads that execute the chatter protocol.
     */
    private final List<StoppableThread> chatterThreads = new LinkedList<>();

    /**
     * A list of threads that execute the sync protocol using bidirectional connections
     */
    private final List<StoppableThread> syncProtocolThreads = new ArrayList<>();

    /**
     * All components that need to be started or that have dispatch observers.
     */
    private final PlatformComponents components;

    /**
     * Call this when a reconnect has been completed.
     */
    private final ReconnectStateLoadedTrigger reconnectStateLoadedDispatcher;

    /**
     * Call this when a state has been loaded from disk.
     */
    private final DiskStateLoadedTrigger diskStateLoadedDispatcher;
    /**
     * The current version of the app running on this platform.
     */
    private final SoftwareVersion appVersion;
    /**
     * For passing notifications between the platform and the application.
     */
    private final NotificationEngine notificationEngine;
    /**
     * This should be used when reading time.
     */
    private final Time time = OSTime.getInstance();

    /**
     * The platform context for this platform. Should be used to access basic services
     */
    private final PlatformContext platformContext;

    /**
     * Writes pre-consensus events to disk.
     */
    private final PreConsensusEventWriter preConsensusEventWriter;

    /**
     * True if gossip has been halted. Only relevant for sync-as-a-protocol
     */
    private final AtomicBoolean gossipHalted = new AtomicBoolean(false);

    /**
     * The permit provider for syncs. Only relevant when sync is running as a protocol
     */
    private SyncPermitProvider syncPermitProvider;

    /**
     * the browser gives the Platform what app to run. There can be multiple Platforms on one computer.
     *
     * @param instanceNumber           this is the Nth copy of the Platform running on this machine (N=instanceNumber)
     * @param parameters               parameters given to the Platform at the start, for app to use
     * @param crypto                   an object holding all the public/private key pairs and the CSPRNG state for this
     *                                 member
     * @param swirldId                 the ID of the swirld being run
     * @param id                       the ID number for this member (if this computer has multiple members in one
     *                                 swirld)
     * @param initialAddressBook       the address book listing all members in the community
     * @param platformContext          the context for this platform
     * @param mainClassName            the name of the app class inheriting from SwirldMain
     * @param swirldName               the name of the swirld being run
     * @param appVersion               the current version of the running application
     * @param genesisStateBuilder      used to construct a genesis state if no suitable state from disk can be found
     * @param loadedSignedState        used to initialize the loaded state
     * @param emergencyRecoveryManager used in emergency recovery.
     */
    SwirldsPlatform(
            final int instanceNumber,
            @NonNull final String[] parameters,
            @NonNull final Crypto crypto,
            @NonNull final byte[] swirldId,
            @NonNull final NodeId id,
            @NonNull final AddressBook initialAddressBook,
            @NonNull final PlatformContext platformContext,
            @NonNull final String platformName,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final ReservedSignedState loadedSignedState,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        this.platformContext = Objects.requireNonNull(platformContext, "platformContext");

        final DispatchBuilder dispatchBuilder =
                new DispatchBuilder(platformContext.getConfiguration().getConfigData(DispatchConfiguration.class));

        components = new PlatformComponents(dispatchBuilder);

        // FUTURE WORK: use a real thread manager here
        threadManager = getStaticThreadManager();

        notificationEngine = NotificationEngine.buildEngine(threadManager);

        dispatchBuilder.registerObservers(this);

        reconnectStateLoadedDispatcher =
                dispatchBuilder.getDispatcher(this, ReconnectStateLoadedTrigger.class)::dispatch;
        diskStateLoadedDispatcher = dispatchBuilder.getDispatcher(this, DiskStateLoadedTrigger.class)::dispatch;

        this.emergencyRecoveryManager = emergencyRecoveryManager;

        this.simultaneousSyncThrottle =
                new SimultaneousSyncThrottle(settings.getMaxIncomingSyncsInc() + settings.getMaxOutgoingSyncs());

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        this.appVersion = appVersion;

        this.instanceNumber = instanceNumber;
        this.parameters = parameters;
        // the memberId of the member running this Platform object
        this.selfId = id;
        // set here, then given to the state in run(). A copy of it is given to hashgraph.
        this.initialAddressBook = initialAddressBook;

        this.eventMapper = new EventMapper(selfId);
        this.chatterEventMapper = new ChatterEventMapper();

        this.metrics = platformContext.getMetrics();
        this.platformName = platformName;

        metrics.getOrCreate(StatConstructor.createEnumStat(
                "PlatformStatus", Metrics.PLATFORM_CATEGORY, PlatformStatus.values(), currentPlatformStatus::get));

        this.platformMetrics = new PlatformMetrics(this);
        metrics.addUpdater(platformMetrics::update);
        this.consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);
        this.eventIntakeMetrics = new EventIntakeMetrics(metrics, time);
        this.syncMetrics = new SyncMetrics(metrics);
        this.networkMetrics =
                new NetworkMetrics(metrics, selfId, getAddressBook().getSize());
        metrics.addUpdater(networkMetrics::update);
        this.reconnectMetrics = new ReconnectMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        if (chatterConfig.useChatter()) {
            chatterCore = new ChatterCore<>(
                    time,
                    GossipEvent.class,
                    new PrepareChatterEvent(CryptographyHolder.get()),
                    chatterConfig,
                    networkMetrics::recordPingTime,
                    metrics);
        } else {
            chatterCore = null;
        }

        this.shadowGraph = new ShadowGraph(syncMetrics, initialAddressBook.getSize());

        this.swirldId = swirldId.clone();
        this.crypto = crypto;

        startUpEventFrozenManager = new StartUpEventFrozenManager(metrics, Instant::now);
        freezeManager = new FreezeManager(this::checkPlatformStatus);

        // Manually wire components for now.
        final ManualWiring wiring = new ManualWiring(platformContext, threadManager, getAddressBook(), freezeManager);
        metrics.addUpdater(wiring::updateMetrics);
        final AppCommunicationComponent appCommunicationComponent =
                wiring.wireAppCommunicationComponent(notificationEngine);

        preConsensusEventWriter = components.add(buildPreConsensusEventWriter());

        stateManagementComponent = wiring.wireStateManagementComponent(
                PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                actualMainClassName,
                selfId,
                swirldName,
                this::createPrioritySystemTransaction,
                this::haltRequested,
                appCommunicationComponent,
                preConsensusEventWriter,
                currentPlatformStatus::get);
        wiring.registerComponents(components);

        final NetworkStatsTransmitter networkStatsTransmitter =
                new NetworkStatsTransmitter(platformContext, this::createSystemTransaction, networkMetrics);
        components.add(networkStatsTransmitter);

        preConsensusSystemTransactionManager = new PreConsensusSystemTransactionManagerFactory()
                .addHandlers(stateManagementComponent.getPreConsensusHandleMethods())
                .build();

        postConsensusSystemTransactionManager = new PostConsensusSystemTransactionManagerFactory()
                .addHandlers(stateManagementComponent.getPostConsensusHandleMethods())
                .build();

        consensusRef = new AtomicReference<>();

        reconnectThrottle = new ReconnectThrottle(settings.getReconnect());

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        topology = new StaticTopology(
                selfId,
                initialAddressBook.getSize(),
                settings.getNumConnections(),
                // unidirectional connections are ONLY used for old-style syncs, that don't run as a protocol
                !chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled());

        fallenBehindManager = new FallenBehindManagerImpl(
                selfId,
                topology.getConnectionGraph(),
                this::checkPlatformStatus,
                () -> {
                    // if we are using old-style syncs, don't start the reconnect controller
                    if (!chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled()) {
                        return;
                    }
                    reconnectController.get().start();
                },
                settings.getReconnect());

        // FUTURE WORK remove this when there are no more ShutdownRequestedTriggers being dispatched
        components.add(new Shutdown());

        // if this setting is 0 or less, there is no startup freeze
        if (settings.getFreezeSecondsAfterStartup() > 0) {
            final Instant startUpEventFrozenEndTime =
                    Instant.now().plusSeconds(settings.getFreezeSecondsAfterStartup());
            startUpEventFrozenManager.setStartUpEventFrozenEndTime(startUpEventFrozenEndTime);
            logger.info(STARTUP.getMarker(), "startUpEventFrozenEndTime: {}", () -> startUpEventFrozenEndTime);
        }

        final Address address = getSelfAddress();
        final String eventStreamManagerName;
        if (address.getMemo() != null && !address.getMemo().isEmpty()) {
            eventStreamManagerName = address.getMemo();
        } else {
            eventStreamManagerName = String.valueOf(selfId);
        }
        logger.info(STARTUP.getMarker(), "initialize eventStreamManager");
        eventStreamManager = new EventStreamManager<>(
                threadManager,
                getSelfId(),
                this,
                eventStreamManagerName,
                settings.isEnableEventStreaming(),
                settings.getEventsLogDir(),
                settings.getEventsLogPeriod(),
                settings.getEventStreamQueueCapacity(),
                this::isLastEventBeforeRestart);

        if (chatterConfig.useChatter()) {
            criticalQuorum = new CriticalQuorumImpl(initialAddressBook, false, chatterConfig.criticalQuorumSoftening());
        } else {
            criticalQuorum = new CriticalQuorumImpl(initialAddressBook);
        }

        final LoadedState loadedState = initializeLoadedStateFromSignedState(loadedSignedState);
        try (loadedState.signedStateFromDisk) {
            final SignedState signedStateFromDisk = loadedState.signedStateFromDisk.getNullable();

            // Queue thread that stores and handles signed states that need to be hashed and have signatures collected.
            final QueueThread<ReservedSignedState> stateHashSignQueueThread = PlatformConstructor.stateHashSignQueue(
                    threadManager, selfId.getId(), stateManagementComponent::newSignedStateFromTransactions);
            stateHashSignQueueThread.start();

            final State stateToLoad;
            if (signedStateFromDisk != null) {
                logger.debug(STARTUP.getMarker(), () -> new SavedStateLoadedPayload(
                                signedStateFromDisk.getRound(),
                                signedStateFromDisk.getConsensusTimestamp(),
                                startUpEventFrozenManager.getStartUpEventFrozenEndTime())
                        .toString());

                stateToLoad = loadedState.initialState;

            } else {
                stateToLoad = buildGenesisState(this, initialAddressBook, appVersion, genesisStateBuilder);

                // if we are not starting from a saved state, don't freeze on startup
                startUpEventFrozenManager.setStartUpEventFrozenEndTime(null);
            }

            if (stateToLoad == null) {
                // this should be impossible
                throw new IllegalStateException("stateToLoad is null");
            }

            swirldStateManager = PlatformConstructor.swirldStateManager(
                    platformContext,
                    initialAddressBook,
                    selfId,
                    preConsensusSystemTransactionManager,
                    postConsensusSystemTransactionManager,
                    metrics,
                    PlatformConstructor.settingsProvider(),
                    freezeManager::isFreezeStarted,
                    stateToLoad);

            // SwirldStateManager will get a copy of the state loaded, that copy will become stateCons.
            // The original state will be saved in the SignedStateMgr and will be deleted when it becomes old

            preConsensusEventHandler = components.add(PlatformConstructor.preConsensusEventHandler(
                    threadManager, selfId, swirldStateManager, consensusMetrics));
            consensusRoundHandler = components.add(PlatformConstructor.consensusHandler(
                    platformContext,
                    threadManager,
                    selfId.getId(),
                    PlatformConstructor.settingsProvider(),
                    swirldStateManager,
                    new ConsensusHandlingMetrics(metrics, time),
                    eventStreamManager,
                    stateHashSignQueueThread,
                    preConsensusEventWriter::waitUntilDurable,
                    freezeManager::freezeStarted,
                    stateManagementComponent::roundAppliedToState,
                    appVersion));

            if (signedStateFromDisk != null) {
                consensusRoundHandler.loadDataFromSignedState(signedStateFromDisk, false);
            }

            final AddedEventMetrics addedEventMetrics = new AddedEventMetrics(this.selfId, metrics);
            final PreconsensusEventStreamSequencer sequencer = new PreconsensusEventStreamSequencer();

            final EventObserverDispatcher dispatcher = new EventObserverDispatcher(
                    new ShadowGraphEventObserver(shadowGraph),
                    consensusRoundHandler,
                    preConsensusEventHandler,
                    eventMapper,
                    addedEventMetrics,
                    criticalQuorum,
                    eventIntakeMetrics,
                    (PreConsensusEventObserver) event -> {
                        sequencer.assignStreamSequenceNumber(event);
                        abortAndThrowIfInterrupted(
                                preConsensusEventWriter::writeEvent,
                                event,
                                "Interrupted while attempting to enqueue preconsensus event for writing");
                    },
                    (ConsensusRoundObserver) round -> {
                        abortAndThrowIfInterrupted(
                                preConsensusEventWriter::setMinimumGenerationNonAncient,
                                round.getGenerations().getMinGenerationNonAncient(),
                                "Interrupted while attempting to enqueue change in minimum generation non-ancient");

                        abortAndThrowIfInterrupted(
                                preConsensusEventWriter::requestFlush,
                                "Interrupted while requesting preconsensus event flush");
                    });
            if (chatterConfig.useChatter()) {
                dispatcher.addObserver(new ChatterNotifier(selfId, chatterCore));
                dispatcher.addObserver(chatterEventMapper);
            }

            final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);

            final List<Predicate<ChatterEventDescriptor>> isDuplicateChecks = new ArrayList<>();
            isDuplicateChecks.add(d -> shadowGraph.isHashInGraph(d.getHash()));
            if (chatterConfig.useChatter()) {
                final OrphanBufferingLinker orphanBuffer = new OrphanBufferingLinker(
                        platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                        parentFinder,
                        chatterConfig.futureGenerationLimit());
                metrics.getOrCreate(
                        new FunctionGauge.Config<>("intake", "numOrphans", Integer.class, orphanBuffer::getNumOrphans)
                                .withDescription("the number of events without parents buffered")
                                .withFormat("%d"));
                eventLinker = orphanBuffer;
                // when using chatter an event could be an orphan, in this case it will be stored in the orphan set
                // when its parents are found, or become ancient, it will move to the shadowgraph
                // non-orphans are also stored in the shadowgraph
                // to dedupe, we need to check both
                isDuplicateChecks.add(orphanBuffer::isOrphan);
            } else {
                eventLinker = new InOrderLinker(
                        platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                        parentFinder,
                        eventMapper::getMostRecentEvent);
            }

            final IntakeCycleStats intakeCycleStats = new IntakeCycleStats(time, metrics);
            final EventIntake eventIntake = new EventIntake(
                    selfId,
                    eventLinker,
                    consensusRef::get,
                    initialAddressBook,
                    dispatcher,
                    intakeCycleStats,
                    shadowGraph);

            final EventCreator eventCreator;
            if (chatterConfig.useChatter()) {
                // chatter has a separate event creator in a different thread. having 2 event creators creates the risk
                // of forking, so a NPE is preferable to a fork
                eventCreator = null;
            } else {
                eventCreator = new EventCreator(
                        this.appVersion,
                        selfId,
                        PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                        consensusRef::get,
                        swirldStateManager.getTransactionPool(),
                        eventIntake::addEvent,
                        eventMapper,
                        eventMapper,
                        swirldStateManager.getTransactionPool(),
                        freezeManager::isFreezeStarted,
                        new EventCreationRules(List.of()));
            }

            final List<GossipEventValidator> validators = new ArrayList<>();
            // it is very important to discard ancient events, otherwise the deduplication will not work, since it
            // doesn't
            // track ancient events
            validators.add(new AncientValidator(consensusRef::get));
            validators.add(new EventDeduplication(isDuplicateChecks, eventIntakeMetrics));
            validators.add(StaticValidators::isParentDataValid);
            validators.add(new TransactionSizeValidator(settings.getMaxTransactionBytesPerEvent()));
            if (settings.isVerifyEventSigs()) {
                validators.add(new SignatureValidator(initialAddressBook));
            }
            final GossipEventValidators eventValidators = new GossipEventValidators(validators);

            /* validates events received from gossip */
            final EventValidator eventValidator = new EventValidator(eventValidators, eventIntake::addUnlinkedEvent);

            final EventTaskDispatcher taskDispatcher = new EventTaskDispatcher(
                    time,
                    eventValidator,
                    eventCreator,
                    eventIntake::addUnlinkedEvent,
                    eventIntakeMetrics,
                    intakeCycleStats);

            final InterruptableConsumer<EventIntakeTask> intakeHandler;
            if (chatterConfig.useChatter()) {
                intakeCycle = new SequenceCycle<>(taskDispatcher::dispatchTask);
                intakeHandler = intakeCycle;
            } else {
                intakeHandler = taskDispatcher::dispatchTask;
            }

            intakeQueue = components.add(new QueueThreadConfiguration<EventIntakeTask>(threadManager)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("event-intake")
                    .setHandler(intakeHandler)
                    .setCapacity(settings.getEventIntakeQueueSize())
                    .setLogAfterPauseDuration(ConfigurationHolder.getInstance()
                            .get()
                            .getConfigData(ThreadConfig.class)
                            .logStackTracePauseDuration())
                    .enableMaxSizeMetric(metrics)
                    .build());

            if (signedStateFromDisk != null) {
                loadIntoConsensusAndEventMapper(signedStateFromDisk);
                eventLinker.loadFromSignedState(signedStateFromDisk);
            } else {
                consensusRef.set(new ConsensusImpl(
                        platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                        consensusMetrics,
                        consensusRoundHandler::addMinGenInfo,
                        getAddressBook()));
            }
        }

        transactionSubmitter = new SwirldTransactionSubmitter(
                currentPlatformStatus::get,
                PlatformConstructor.settingsProvider(),
                swirldStateManager::submitTransaction,
                new TransactionMetrics(metrics));

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        Pair.of(getIntakeQueue(), "intakeQueue"),
                        Pair.of(getEventMapper(), "eventMapper"),
                        Pair.of(getShadowGraph(), "shadowGraph"),
                        Pair.of(preConsensusEventHandler, "preConsensusEventHandler"),
                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
                        Pair.of(swirldStateManager, "swirldStateManager")));
    }

    /**
     * Check if the platform was started from genesis.
     *
     * @return true if the platform was started from genesis, false if it was started from a saved state
     */
    public boolean isStartedFromGenesis() {
        return startedFromGenesis;
    }

    /**
     * If there are multiple platforms running in the same JVM, each will have a unique instance number.
     *
     * @return this platform's instance number
     */
    @Override
    public int getInstanceNumber() {
        return instanceNumber;
    }

    /**
     * Get the transactionMaxBytes in Settings
     *
     * @return integer representing the maximum number of bytes allowed in a transaction
     */
    public static int getTransactionMaxBytes() {
        return Settings.getInstance().getTransactionMaxBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * if it is a Mirror node
     *
     * @return true/false based on if this is mirror node
     */
    public boolean isMirrorNode() {
        return selfId.isMirror();
    }

    /**
     * returns a name for this Platform, which includes the app, the swirld, the member id, and the member name. This is
     * useful in the Browser window for showing data about each of the running Platform objects.
     *
     * @return the name for this Platform
     */
    public String getPlatformName() {
        // this will be the empty string until the Browser calls setInfoMember. Then it will be correct.
        return platformName;
    }

    /**
     * Stores a new system transaction that will be added to an event in the future. Transactions submitted here are not
     * given priority. Any priority transactions waiting to be included in an event will be included first.
     *
     * @param systemTransaction the new system transaction to be included in a future event
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createSystemTransaction(final SystemTransaction systemTransaction) {
        return createSystemTransaction(systemTransaction, false);
    }

    /**
     * Stores a new system transaction that will be added to an event in the future. Transactions submitted here are not
     * given priority, meaning any priority transaction waiting to be included in an event will be selected first.
     *
     * @param systemTransaction the new system transaction to be included in a future event
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean createPrioritySystemTransaction(final SystemTransaction systemTransaction) {
        return createSystemTransaction(systemTransaction, true);
    }

    private boolean createSystemTransaction(final SystemTransaction systemTransaction, final boolean priority) {
        if (systemTransaction == null) {
            return false;
        }

        return swirldStateManager.submitTransaction(systemTransaction, priority);
    }

    /**
     * A container for the initial state.
     *
     * @param signedStateFromDisk the initial signed state loaded from disk
     * @param initialState        the initial {@link State} object. This is a fast copy of the state loaded from disk
     */
    private record LoadedState(@NonNull ReservedSignedState signedStateFromDisk, @Nullable State initialState) {}

    /**
     * Update the address book with the current address book read from config.txt. Eventually we will not do this, and
     * only transactions will be capable of modifying the address book.
     *
     * @param signedState the state that was loaded from disk
     * @param addressBook the address book specified in config.txt
     */
    private static void updateLoadedStateAddressBook(final SignedState signedState, final AddressBook addressBook) {
        final State state = signedState.getState();

        // Update the address book with the current address book read from config.txt.
        // Eventually we will not do this, and only transactions will be capable of
        // modifying the address book.
        state.getPlatformState().setAddressBook(addressBook.copy());

        // Invalidate a path down to the new address book
        new MerkleRouteIterator(state, state.getPlatformState().getAddressBook().getRoute())
                .forEachRemaining(MerkleNode::invalidateHash);

        // We should only have to rehash a few nodes, so simpler to use the synchronous algorithm.
        MerkleCryptoFactory.getInstance().digestTreeSync(state);

        // If our hash changes as a result of the new address book then our old signatures may become invalid.
        signedState.pruneInvalidSignatures();
    }

    /**
     * Create the LoadedState from the SignedState loaded from disk, if it is present.
     *
     * @param signedStateFromDisk the SignedState loaded from disk.
     * @return the LoadedState
     */
    @NonNull
    private LoadedState initializeLoadedStateFromSignedState(@NonNull final ReservedSignedState signedStateFromDisk) {
        try (signedStateFromDisk) {
            if (signedStateFromDisk.isNotNull()) {
                updateLoadedStateAddressBook(signedStateFromDisk.get(), initialAddressBook);
                diskStateHash = signedStateFromDisk.get().getState().getHash();
                diskStateRound = signedStateFromDisk.get().getRound();
                final State initialState = loadSavedState(signedStateFromDisk.get());
                return new LoadedState(
                        signedStateFromDisk.getAndReserve("SwirldsPlatform.initializeLoadedStateFromSignedState()"),
                        initialState);
            } else {
                startedFromGenesis = true;
            }
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Saved state not loaded:", e);
            // if requireStateLoad is on, we exit. if not, we just log it
            if (Settings.getInstance().isRequireStateLoad()) {
                SystemUtils.exitSystem(SystemExitReason.SAVED_STATE_NOT_LOADED);
            }
        }
        return new LoadedState(createNullReservation(), null);
    }

    private State loadSavedState(final SignedState signedStateFromDisk) {
        logger.info(
                STARTUP.getMarker(),
                "Information for state loaded from disk:\n{}\n{}",
                () -> signedStateFromDisk.getState().getPlatformState().getInfoString(),
                () -> new MerkleTreeVisualizer(signedStateFromDisk.getState())
                        .setDepth(StateSettings.getDebugHashDepth())
                        .render());

        /**
         * The previous version of the software that was run. Null if this is the first time running, or if the previous
         * version ran before the concept of application software versioning was introduced.
         */
        final SoftwareVersion previousSoftwareVersion = signedStateFromDisk
                .getState()
                .getPlatformState()
                .getPlatformData()
                .getCreationSoftwareVersion();
        final State initialState = signedStateFromDisk.getState().copy();
        initialState.getPlatformState().getPlatformData().setCreationSoftwareVersion(appVersion);

        final Hash initialHash = initialState.getSwirldState().getHash();
        initialState
                .getSwirldState()
                .init(this, initialState.getSwirldDualState(), InitTrigger.RESTART, previousSoftwareVersion);
        initialState.markAsInitialized();

        final Hash currentHash = initialState.getSwirldState().getHash();

        // If the current hash is null, we must hash the state
        // It's also possible that the application modified the state and hashed itself in init(), if so we need to
        // rehash the state as a whole.
        if (currentHash == null || !Objects.equals(initialHash, currentHash)) {
            initialState.invalidateHash();
            abortAndThrowIfInterrupted(
                    () -> {
                        try {
                            MerkleCryptoFactory.getInstance()
                                    .digestTreeAsync(initialState)
                                    .get();
                        } catch (final ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    "interrupted while attempting to hash the state");
        }

        stateManagementComponent.stateToLoad(signedStateFromDisk, SourceOfSignedState.DISK);

        return initialState;
    }

    /**
     * Loads the signed state data into consensus and event mapper
     *
     * @param signedState the state to get the data from
     */
    void loadIntoConsensusAndEventMapper(final SignedState signedState) {
        consensusRef.set(new ConsensusImpl(
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class),
                consensusMetrics,
                consensusRoundHandler::addMinGenInfo,
                getAddressBook(),
                signedState));

        shadowGraph.initFromEvents(
                EventUtils.prepareForShadowGraph(
                        // we need to pass in a copy of the array, otherwise prepareForShadowGraph will rearrange the
                        // events in the signed state which will cause issues for other components that depend on it
                        signedState.getEvents().clone()),
                // we need to provide the minGen from consensus so that expiry matches after a restart/reconnect
                consensusRef.get().getMinRoundGeneration());

        // Data that is needed for the intake system to work
        for (final EventImpl e : signedState.getEvents()) {
            eventMapper.eventAdded(e);
        }

        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        if (chatterConfig.useChatter()) {
            chatterEventMapper.loadFromSignedState(signedState);
            chatterCore.loadFromSignedState(signedState);
        }

        logger.info(
                STARTUP.getMarker(),
                "Last known events after restart are {}",
                eventMapper.getMostRecentEventsByEachCreator());
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    void loadReconnectState(final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        logger.info(
                LogMarker.STATE_HASH.getMarker(),
                "{}RECONNECT: loadReconnectState: reloading state",
                HASH_STREAM_OPERATION_PREFIX);
        logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
        try {

            reconnectStateLoadedDispatcher.dispatch(
                    signedState.getRound(), signedState.getState().getHash());

            // It's important to call init() before loading the signed state. The loading process makes copies
            // of the state, and we want to be sure that the first state in the chain of copies has been initialized.
            final Hash reconnectHash = signedState.getState().getHash();
            signedState
                    .getSwirldState()
                    .init(
                            this,
                            signedState.getState().getSwirldDualState(),
                            InitTrigger.RECONNECT,
                            signedState
                                    .getState()
                                    .getPlatformState()
                                    .getPlatformData()
                                    .getCreationSoftwareVersion());
            if (!Objects.equals(signedState.getState().getHash(), reconnectHash)) {
                throw new IllegalStateException(
                        "State hash is not permitted to change during a reconnect init() call. Previous hash was "
                                + reconnectHash + ", new hash is "
                                + signedState.getState().getHash());
            }
            signedState.getState().markAsInitialized();

            swirldStateManager.loadFromSignedState(signedState);

            stateManagementComponent.stateToLoad(signedState, SourceOfSignedState.RECONNECT);

            loadIntoConsensusAndEventMapper(signedState);
            // eventLinker is not thread safe, which is not a problem regularly because it is only used by a single
            // thread. after a reconnect, it needs to load the minimum generation from a state on a different thread,
            // so the intake thread is paused before the data is loaded and unpaused after. this ensures that the
            // thread will get the up-to-date data loaded
            new PauseAndLoad(getIntakeQueue(), eventLinker).loadFromSignedState(signedState);

            getConsensusHandler().loadDataFromSignedState(signedState, true);

            // Notify any listeners that the reconnect has been completed
            notificationEngine.dispatch(
                    ReconnectCompleteListener.class,
                    new ReconnectCompleteNotification(
                            signedState.getRound(),
                            signedState.getConsensusTimestamp(),
                            signedState.getState().getSwirldState()));
        } catch (final RuntimeException e) {
            logger.debug(RECONNECT.getMarker(), "`loadReconnectState` : FAILED, reason: {}", e.getMessage());
            // if the loading fails for whatever reason, we clear all data again in case some of it has been loaded
            clearAllPipelines.clear();
            throw e;
        }

        logger.debug(
                RECONNECT.getMarker(),
                "`loadReconnectState` : reconnect complete notifications finished. Resetting fallen-behind");
        getSyncManager().resetFallenBehind();
        logger.debug(
                RECONNECT.getMarker(),
                "`loadReconnectState` : resetting fallen-behind & reloading state, finished, succeeded`");
    }

    /**
     * This observer is called when a system freeze is requested. Permanently stops event creation and gossip.
     *
     * @param reason the reason why the system is being frozen.
     */
    private void haltRequested(final String reason) {
        logger.error(EXCEPTION.getMarker(), "System halt requested. Reason: {}", reason);
        freezeManager.freezeEventCreation();
        for (final StoppableThread thread : chatterThreads) {
            thread.stop();
        }
        for (final StoppableThread thread : syncProtocolThreads) {
            thread.stop();
        }

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        if (syncConfig.syncAsProtocolEnabled()) {
            gossipHalted.set(true);
        }

        if (syncManager != null) {
            syncManager.haltRequestedObserver(reason);
        }
    }

    /**
     * Build the pre-consensus event writer.
     */
    private PreConsensusEventWriter buildPreConsensusEventWriter() {
        final PreConsensusEventStreamConfig preConsensusEventStreamConfig =
                platformContext.getConfiguration().getConfigData(PreConsensusEventStreamConfig.class);

        if (!preConsensusEventStreamConfig.enableStorage()) {
            return new NoOpPreConsensusEventWriter();
        }

        final PreConsensusEventFileManager fileManager;
        try {
            fileManager = new PreConsensusEventFileManager(platformContext, OSTime.getInstance(), selfId.getId());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable load preconsensus files", e);
        }

        final PreConsensusEventWriter syncWriter = new SyncPreConsensusEventWriter(platformContext, fileManager);

        return new AsyncPreConsensusEventWriter(platformContext, threadManager, syncWriter);
    }

    /**
     * Start this platform.
     */
    @Override
    public void start() {
        syncManager = components.add(new SyncManagerImpl(
                intakeQueue,
                topology.getConnectionGraph(),
                selfId,
                new EventCreationRules(List.of(
                        selfId, swirldStateManager.getTransactionPool(), startUpEventFrozenManager, freezeManager)),
                criticalQuorum,
                initialAddressBook,
                fallenBehindManager));

        components.start();

        if (!startedFromGenesis) {
            // If we loaded from disk then call the appropriate dispatch. This dispatch
            // must wait until after components have been started.
            diskStateLoadedDispatcher.dispatch(diskStateRound, diskStateHash);

            // Let the app know that a state was loaded.
            notificationEngine.dispatch(
                    StateLoadedFromDiskCompleteListener.class, new StateLoadedFromDiskNotification());
        }

        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            // If there is no default uncaught exception handler already provided, make sure we set one to avoid threads
            // silently dying from exceptions.
            Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) ->
                    logger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e));
        }

        this.eventTaskCreator = new EventTaskCreator(
                eventMapper,
                // hashgraph and state get separate copies of the address book
                initialAddressBook.copy(),
                selfId,
                eventIntakeMetrics,
                intakeQueue,
                StaticSettingsProvider.getSingleton(),
                syncManager,
                ThreadLocalRandom::current);

        // a genesis event could be created here, but it isn't needed. This member will naturally create an
        // event after their first sync, where the first sync will involve sending no events.

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
        shadowgraphExecutor.start();
        syncShadowgraphSynchronizer = new ShadowGraphSynchronizer(
                getShadowGraph(),
                getAddressBook().getSize(),
                syncMetrics,
                consensusRef::get,
                eventTaskCreator::syncDone,
                eventTaskCreator::addEvent,
                syncManager,
                shadowgraphExecutor,
                // don't send or receive init bytes if running sync as a protocol. the negotiator handles this
                !syncConfig.syncAsProtocolEnabled(),
                () -> {});

        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        syncPermitProvider = new SyncPermitProvider(syncConfig.syncProtocolPermitCount());

        final Runnable stopGossip;
        if (chatterConfig.useChatter()) {
            stopGossip = chatterCore::stopChatter;
        } else if (syncConfig.syncAsProtocolEnabled()) {
            stopGossip = () -> {
                gossipHalted.set(true);
                // wait for all existing syncs to stop. no new ones will be started, since gossip has been halted, and
                // we've fallen behind
                syncPermitProvider.waitForAllSyncsToFinish();
            };
        } else {
            // wait and acquire all sync ongoing locks and release them immediately
            // this will ensure any ongoing sync are finished before we start reconnect
            // no new sync will start because we have a fallen behind status
            stopGossip = getSimultaneousSyncThrottle()::waitForAllSyncsToFinish;
        }

        reconnectHelper = new ReconnectHelper(
                stopGossip,
                clearAllPipelines,
                getSwirldStateManager()::getConsensusState,
                stateManagementComponent::getLastCompleteRound,
                new ReconnectLearnerThrottle(selfId, settings.getReconnect()),
                this::loadReconnectState,
                new ReconnectLearnerFactory(
                        platformContext, threadManager, initialAddressBook, settings.getReconnect(), reconnectMetrics));
        if (chatterConfig.useChatter()) {
            reconnectController.set(new ReconnectController(threadManager, reconnectHelper, chatterCore::startChatter));
            startChatterNetwork();
        } else {
            startSyncNetwork();
        }

        metrics.start();

        if (settings.isRunPauseCheckTimer()) {
            // periodically check current time stamp to detect whether the java application
            // has been paused for a long period
            final Timer pauseCheckTimer = new Timer("pause check", true);
            pauseCheckTimer.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            final long currentTimeStamp = System.currentTimeMillis();
                            if ((currentTimeStamp - pauseCheckTimeStamp) > PAUSE_ALERT_INTERVAL
                                    && pauseCheckTimeStamp != 0) {
                                logger.error(
                                        EXCEPTION.getMarker(),
                                        "ERROR, a pause larger than {} is detected ",
                                        PAUSE_ALERT_INTERVAL);
                            }
                            pauseCheckTimeStamp = currentTimeStamp;
                        }
                    },
                    0,
                    PAUSE_ALERT_INTERVAL / 2);
        }

        // FUTURE WORK: validate that status is still STARTING_UP (sanity check until we refactor platform status)
        // FUTURE WORK: set platform status REPLAYING_EVENTS
        // FUTURE WORK: replay the preconsensus event stream
        // FUTURE WORK: validate that status is still REPLAYING_EVENTS (sanity check until we refactor platform status)
        abortAndThrowIfInterrupted(
                preConsensusEventWriter::beginStreamingNewEvents,
                "interrupted while attempting to begin streaming new preconsensus events");
        // FUTURE WORK: set platform status READY
        // FUTURE WORK: start gossip & new event creation here

        // in case of a single node network, the platform status update will not be triggered by connections, so it
        // needs to be triggered now
        checkPlatformStatus();
    }

    /**
     * Construct and start all networking components that are common to both types of networks, sync and chatter. This
     * is everything related to creating and managing connections to neighbors.Only the connection managers are returned
     * since this should be the only point of entry for other components.
     *
     * @return an instance that maintains connection managers for all connections to neighbors
     */
    public StaticConnectionManagers startCommonNetwork() {
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final SocketFactory socketFactory = PlatformConstructor.socketFactory(
                crypto.getKeysAndCerts(), platformContext.getConfiguration().getConfigData(CryptoConfig.class));
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);

        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
                selfId,
                StaticSettingsProvider.getSingleton(),
                this,
                socketFactory,
                initialAddressBook,
                // only do a version check for old-style sync
                !chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled(),
                appVersion);
        final StaticConnectionManagers connectionManagers = new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
                this,
                selfId,
                initialAddressBook,
                connectionManagers::newConnection,
                StaticSettingsProvider.getSingleton(),
                // only do a version check for old-style sync
                !chatterConfig.useChatter() && !syncConfig.syncAsProtocolEnabled(),
                appVersion);
        // allow other members to create connections to me
        final Address address = getSelfAddress();
        final ConnectionServer connectionServer = new ConnectionServer(
                threadManager,
                address.getListenAddressIpv4(),
                address.getListenPortIpv4(),
                socketFactory,
                inboundConnectionHandler::handle);
        new StoppableThreadConfiguration<>(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.getId())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build()
                .start();
        return connectionManagers;
    }

    /**
     * Constructs and starts all networking components needed for a chatter network to run: readers, writers and a
     * separate event creation thread.
     */
    public void startChatterNetwork() {
        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);

        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        // first create all instances because of thread safety
        for (final NodeId otherId : topology.getNeighbors()) {
            chatterCore.newPeerInstance(otherId.getId(), eventTaskCreator::addEvent);
        }

        // If we still need an emergency recovery state, we need it via emergency reconnect.
        // Start the helper now so that it is ready to receive a connection to perform reconnect with when the
        // protocol is initiated.
        // This must be after all chatter peer instances are created so that the chatter comm state can be suspended
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            reconnectController.get().start();
        }

        final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor(threadManager, "chatter");
        parallelExecutor.start();
        for (final NodeId otherId : topology.getNeighbors()) {
            final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId.getId());
            final ParallelExecutor shadowgraphExecutor = PlatformConstructor.parallelExecutor(threadManager);
            shadowgraphExecutor.start();
            final ShadowGraphSynchronizer chatterSynchronizer = new ShadowGraphSynchronizer(
                    getShadowGraph(),
                    getAddressBook().getSize(),
                    syncMetrics,
                    consensusRef::get,
                    sr -> {},
                    eventTaskCreator::addEvent,
                    syncManager,
                    shadowgraphExecutor,
                    false,
                    () -> {
                        // start accepting events into the chatter queue
                        chatterPeer.communicationState().chatterSyncStartingPhase3();
                        // wait for any intake event currently being processed to finish
                        intakeCycle.waitForCurrentSequenceEnd();
                    });

            final ChatterConfig chatterConfig =
                    platformContext.getConfiguration().getConfigData(ChatterConfig.class);

            chatterThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("ChatterReader")
                    .setHangingThreadPeriod(basicConfig.hangingThreadDuration())
                    .setWork(new NegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
                            chatterConfig.sleepAfterFailedNegotiation(),
                            List.of(
                                    new VersionCompareHandshake(appVersion, !settings.isGossipWithDifferentVersions()),
                                    new VersionCompareHandshake(
                                            PlatformVersion.locateOrDefault(),
                                            !settings.isGossipWithDifferentVersions())),
                            new NegotiationProtocols(List.of(
                                    new EmergencyReconnectProtocol(
                                            threadManager,
                                            notificationEngine,
                                            otherId,
                                            emergencyRecoveryManager,
                                            reconnectThrottle,
                                            stateManagementComponent,
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController.get()),
                                    new ReconnectProtocol(
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> stateManagementComponent.getLatestSignedState(
                                                    "SwirldsPlatform: ReconnectProtocol"),
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController.get(),
                                            new DefaultSignedStateValidator(),
                                            fallenBehindManager),
                                    new ChatterSyncProtocol(
                                            otherId,
                                            chatterPeer.communicationState(),
                                            chatterPeer.outputAggregator(),
                                            chatterSynchronizer,
                                            fallenBehindManager),
                                    new ChatterProtocol(chatterPeer, parallelExecutor)))))
                    .build(true));
        }
        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final ChatterConfig chatterConfig = platformContext.getConfiguration().getConfigData(ChatterConfig.class);
        final EventCreationRules eventCreationRules = LoggingEventCreationRules.create(
                List.of(
                        startUpEventFrozenManager,
                        freezeManager,
                        fallenBehindManager,
                        new ChatteringRule(
                                chatterConfig.chatteringCreationThreshold(),
                                chatterCore.getPeerInstances().stream()
                                        .map(PeerInstance::communicationState)
                                        .toList()),
                        swirldStateManager.getTransactionPool(),
                        new BelowIntCreationRule(intakeQueue::size, chatterConfig.chatterIntakeThrottle())),
                List.of(
                        StaticCreationRules::nullOtherParent,
                        otherParentTracker,
                        new AncientParentsRule(consensusRef::get),
                        criticalQuorum));
        final ChatterEventCreator chatterEventCreator = new ChatterEventCreator(
                appVersion,
                selfId,
                PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                swirldStateManager.getTransactionPool(),
                combineConsumers(
                        eventTaskCreator::createdEvent, otherParentTracker::track, chatterEventMapper::mapEvent),
                chatterEventMapper::getMostRecentEvent,
                eventCreationRules,
                CryptographyHolder.get(),
                OSTime.getInstance());

        if (isStartedFromGenesis()) {
            // if we are starting from genesis, we will create a genesis event, which is the only event that will
            // ever be created without an other-parent
            chatterEventCreator.createGenesisEvent();
        }
        final EventCreatorThread eventCreatorThread = new EventCreatorThread(
                threadManager,
                selfId,
                chatterConfig.attemptedChatterEventPerSecond(),
                initialAddressBook,
                chatterEventCreator::createEvent,
                CryptoStatic.getNonDetRandom());

        clearAllPipelines = new LoggingClearables(
                RECONNECT.getMarker(),
                List.of(
                        // chatter event creator needs to be cleared first, because it sends event to intake
                        Pair.of(eventCreatorThread, "eventCreatorThread"),
                        Pair.of(getIntakeQueue(), "intakeQueue"),
                        // eventLinker is not thread safe, so the intake thread needs to be paused while its being
                        // cleared
                        Pair.of(new PauseAndClear(getIntakeQueue(), eventLinker), "eventLinker"),
                        Pair.of(eventMapper, "eventMapper"),
                        Pair.of(chatterEventMapper, "chatterEventMapper"),
                        Pair.of(getShadowGraph(), "shadowGraph"),
                        Pair.of(preConsensusEventHandler, "preConsensusEventHandler"),
                        Pair.of(consensusRoundHandler, "consensusRoundHandler"),
                        Pair.of(swirldStateManager, "swirldStateManager")));
        eventCreatorThread.start();
    }

    /**
     * Constructs and starts all networking components needed for a sync network to run: heartbeats, callers, listeners
     */
    public void startSyncNetwork() {
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        if (syncConfig.syncAsProtocolEnabled()) {
            reconnectController.set(
                    new ReconnectController(threadManager, reconnectHelper, () -> gossipHalted.set(false)));
            startSyncAsProtocolNetwork(connectionManagers);
            return;
        }

        sharedConnectionLocks = new SharedConnectionLocks(topology, connectionManagers);
        final MultiProtocolResponder protocolHandlers = new MultiProtocolResponder(List.of(
                ProtocolMapping.map(
                        UnidirectionalProtocols.SYNC.getInitialByte(),
                        new SyncProtocolResponder(
                                simultaneousSyncThrottle,
                                syncShadowgraphSynchronizer,
                                syncManager,
                                syncManager::shouldAcceptSync)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.RECONNECT.getInitialByte(),
                        new ReconnectProtocolResponder(
                                threadManager,
                                stateManagementComponent,
                                settings.getReconnect(),
                                reconnectThrottle,
                                reconnectMetrics)),
                ProtocolMapping.map(
                        UnidirectionalProtocols.HEARTBEAT.getInitialByte(),
                        HeartbeatProtocolResponder::heartbeatProtocol)));

        for (final NodeId otherId : topology.getNeighbors()) {
            // create and start new threads to listen for incoming sync requests
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("listener")
                    .setWork(new Listener(protocolHandlers, connectionManagers.getManager(otherId, false)))
                    .build()
                    .start();

            // create and start new thread to send heartbeats on the SyncCaller channels
            new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(settings.getThreadPrioritySync())
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("heartbeat")
                    .setOtherNodeId(otherId.getId())
                    .setWork(new HeartbeatSender(
                            otherId, sharedConnectionLocks, networkMetrics, PlatformConstructor.settingsProvider()))
                    .build()
                    .start();
        }

        // start the timing AFTER the initial pause
        metrics.resetAll();
        // create and start threads to call other members
        for (int i = 0; i < settings.getMaxOutgoingSyncs(); i++) {
            spawnSyncCaller(i);
        }
    }

    /**
     * Starts sync as a protocol network, with work managed by a {@link NegotiatorThread}
     * <p>
     * Starting sync with this method supports emergency reconnect
     *
     * @param connectionManagers the constructed connection managers
     */
    private void startSyncAsProtocolNetwork(@NonNull final StaticConnectionManagers connectionManagers) {
        Objects.requireNonNull(connectionManagers);

        final BasicConfig basicConfig = platformContext.getConfiguration().getConfigData(BasicConfig.class);
        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        final Duration hangingThreadDuration = basicConfig.hangingThreadDuration();

        // if this is a single node network, start dedicated thread to "sync" and create events
        if (initialAddressBook.getSize() == 1) {
            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(selfId.getId())
                    .setThreadName("SingleNodeNetworkSync")
                    .setHangingThreadPeriod(hangingThreadDuration)
                    .setWork(new SingleNodeNetworkSync(
                            this::checkPlatformStatus,
                            eventTaskCreator::createEvent,
                            this::getSleepAfterSync,
                            selfId.getId()))
                    .build(true));

            return;
        }

        // If we still need an emergency recovery state, we need it via emergency reconnect.
        // Start the helper now so that it is ready to receive a connection to perform reconnect with when the
        // protocol is initiated.
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            reconnectController.get().start();
        }

        final PeerAgnosticSyncChecks peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(
                () -> !gossipHalted.get(), () -> intakeQueue.size() < settings.getEventIntakeQueueThrottleSize()));

        for (final NodeId otherId : topology.getNeighbors()) {
            syncProtocolThreads.add(new StoppableThreadConfiguration<>(threadManager)
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("SyncProtocolWith" + otherId.getId())
                    .setHangingThreadPeriod(hangingThreadDuration)
                    .setWork(new NegotiatorThread(
                            connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
                            syncConfig.syncSleepAfterFailedNegotiation(),
                            List.of(
                                    new VersionCompareHandshake(appVersion, !settings.isGossipWithDifferentVersions()),
                                    new VersionCompareHandshake(
                                            PlatformVersion.locateOrDefault(),
                                            !settings.isGossipWithDifferentVersions())),
                            new NegotiationProtocols(List.of(
                                    new HeartbeatProtocol(
                                            otherId,
                                            Duration.ofMillis(syncConfig.syncProtocolHeartbeatPeriod()),
                                            networkMetrics,
                                            time),
                                    new EmergencyReconnectProtocol(
                                            threadManager,
                                            notificationEngine,
                                            otherId,
                                            emergencyRecoveryManager,
                                            reconnectThrottle,
                                            stateManagementComponent,
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController.get()),
                                    new ReconnectProtocol(
                                            threadManager,
                                            otherId,
                                            reconnectThrottle,
                                            () -> stateManagementComponent.getLatestSignedState(
                                                    "SwirldsPlatform: ReconnectProtocol"),
                                            settings.getReconnect().getAsyncStreamTimeoutMilliseconds(),
                                            reconnectMetrics,
                                            reconnectController.get(),
                                            new DefaultSignedStateValidator(),
                                            fallenBehindManager),
                                    new SyncProtocol(
                                            otherId,
                                            syncShadowgraphSynchronizer,
                                            fallenBehindManager,
                                            syncPermitProvider,
                                            criticalQuorum,
                                            peerAgnosticSyncChecks,
                                            Duration.ofMillis(getSleepAfterSync()),
                                            syncMetrics,
                                            time)))))
                    .build(true));
        }
    }

    /**
     * Spawn a thread to initiate syncs with other users
     */
    private void spawnSyncCaller(final int callerNumber) {
        // create a caller that will run repeatedly to call random members other than selfId
        final SyncCaller syncCaller = new SyncCaller(
                this,
                getAddressBook(),
                selfId,
                callerNumber,
                reconnectHelper,
                new DefaultSignedStateValidator(),
                platformMetrics);

        /* the thread that repeatedly initiates syncs with other members */
        final Thread syncCallerThread = new ThreadConfiguration(threadManager)
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.getId())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("syncCaller-" + callerNumber)
                .setRunnable(syncCaller)
                .build();

        syncCallerThread.start();
    }

    /**
     * {@inheritDoc}
     */
    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    /**
     * @return the SyncManager used by this platform
     */
    public SyncManagerImpl getSyncManager() {
        return syncManager;
    }

    /**
     * Get the shadow graph used by this platform
     *
     * @return the {@link ShadowGraph} used by this platform
     */
    public ShadowGraph getShadowGraph() {
        return shadowGraph;
    }

    /**
     * @return the signed state manager for this platform
     */
    public StateManagementComponent getStateManagementComponent() {
        return stateManagementComponent;
    }

    /**
     * @return locks used to synchronize usage of outbound connections
     */
    public SharedConnectionLocks getSharedConnectionLocks() {
        return sharedConnectionLocks;
    }

    /**
     * @return the instance responsible for creating event tasks
     */
    public EventTaskCreator getEventTaskCreator() {
        return eventTaskCreator;
    }

    /**
     * Get the {@link EventMapper} for this platform.
     */
    public EventMapper getEventMapper() {
        return eventMapper;
    }

    /**
     * Get the event intake queue for this platform.
     */
    public QueueThread<EventIntakeTask> getIntakeQueue() {
        return intakeQueue;
    }

    /**
     * @return the consensus object used by this platform
     */
    public Consensus getConsensus() {
        return consensusRef.get();
    }

    /**
     * @return the object that tracks recent events created
     */
    public CriticalQuorum getCriticalQuorum() {
        return criticalQuorum;
    }

    /**
     * Checks the status of the platform and notifies the SwirldMain if there is a change in status
     */
    public void checkPlatformStatus() {
        final int numNodes = initialAddressBook.getSize();

        synchronized (currentPlatformStatus) {
            final PlatformStatus newStatus;
            if (numNodes > 1 && activeConnectionNumber.get() == 0) {
                newStatus = PlatformStatus.DISCONNECTED;
            } else if (getSyncManager().hasFallenBehind()) {
                newStatus = PlatformStatus.BEHIND;
            } else if (freezeManager.isFreezeStarted()) {
                newStatus = PlatformStatus.MAINTENANCE;
            } else if (freezeManager.isFreezeComplete()) {
                newStatus = PlatformStatus.FREEZE_COMPLETE;
            } else {
                newStatus = PlatformStatus.ACTIVE;
            }

            final PlatformStatus oldStatus = currentPlatformStatus.getAndSet(newStatus);
            if (oldStatus != newStatus) {
                final PlatformStatus ns = newStatus;
                logger.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
                                "Platform status changed.", oldStatus == null ? "" : oldStatus.name(), ns.name())
                        .toString());

                logger.info(PLATFORM_STATUS.getMarker(), "Platform status changed to: {}", newStatus.toString());

                notificationEngine.dispatch(
                        PlatformStatusChangeListener.class, new PlatformStatusChangeNotification(newStatus));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newConnectionOpened(final Connection sc) {
        activeConnectionNumber.getAndIncrement();
        checkPlatformStatus();
        networkMetrics.connectionEstablished(sc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectionClosed(final boolean outbound, final Connection conn) {
        final int connectionNumber = activeConnectionNumber.decrementAndGet();
        if (connectionNumber < 0) {
            logger.error(EXCEPTION.getMarker(), "activeConnectionNumber is {}, this is a bug!", connectionNumber);
        }
        checkPlatformStatus();

        if (outbound) {
            platformMetrics.incrementInterruptedCallSyncs();
        } else {
            platformMetrics.incrementInterruptedRecSyncs();
        }
        networkMetrics.recordDisconnect(conn);
    }

    /**
     * @return the instance that manages interactions with the {@link SwirldState}
     */
    public SwirldStateManager getSwirldStateManager() {
        return swirldStateManager;
    }

    /**
     * @return the handler that applies consensus events to state and creates signed states
     */
    public ConsensusRoundHandler getConsensusHandler() {
        return consensusRoundHandler;
    }

    /**
     * @return the handler that applies pre-consensus events to state
     */
    public PreConsensusEventHandler getPreConsensusHandler() {
        return preConsensusEventHandler;
    }

    /** {@inheritDoc} */
    @Override
    public boolean createTransaction(@NonNull final byte[] transaction) {
        return transactionSubmitter.submitTransaction(new SwirldTransaction(transaction));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformEvent[] getAllEvents() {
        // There is currently a race condition that can cause an exception if event order changes at
        // just the right moment. Since this is just a testing utility method and not used in production
        // environments, we can just retry until we succeed.
        int maxRetries = 100;
        while (maxRetries-- > 0) {
            try {
                final EventImpl[] allEvents = shadowGraph.getAllEvents();
                Arrays.sort(allEvents, (o1, o2) -> {
                    if (o1.getConsensusOrder() != -1 && o2.getConsensusOrder() != -1) {
                        // both are consensus
                        return Long.compare(o1.getConsensusOrder(), o2.getConsensusOrder());
                    } else if (o1.getConsensusTimestamp() == null && o2.getConsensusTimestamp() == null) {
                        // neither are consensus
                        return o1.getTimeReceived().compareTo(o2.getTimeReceived());
                    } else {
                        // one is consensus, the other is not
                        if (o1.getConsensusTimestamp() == null) {
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                });
                return allEvents;
            } catch (final IllegalArgumentException e) {
                logger.error(EXCEPTION.getMarker(), "Exception while sorting events", e);
            }
        }
        throw new IllegalStateException("Unable to sort events after 100 retries");
    }

    /**
     * get the highest generation number of all events with the given creator
     *
     * @param creatorId the ID of the node in question
     * @return the highest generation number known stored for the given creator ID
     */
    public long getLastGen(final long creatorId) {
        return eventMapper.getHighestGenerationNumber(creatorId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSleepAfterSync() {
        return delayAfterSync;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSleepAfterSync(final long delay) {
        delayAfterSync = delay;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformContext getContext() {
        return platformContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getParameters() {
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getSwirldId() {
        return swirldId.clone();
    }

    /** {@inheritDoc} */
    @Override
    public Signature sign(final byte[] data) {
        return crypto.sign(data);
    }

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    @Override
    public AddressBook getAddressBook() {
        return initialAddressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getLastSignedStateTimestamp() {
        try (final ReservedSignedState wrapper =
                stateManagementComponent.getLatestImmutableState("SwirldsPlatform.getLastSignedStateTimestamp()")) {
            if (wrapper.isNotNull()) {
                return wrapper.get().getConsensusTimestamp();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends SwirldState> T getState() {
        return (T) swirldStateManager.getCurrentSwirldState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseState() {
        swirldStateManager.releaseCurrentSwirldState();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull final String reason) {
        final ReservedSignedState wrapper = stateManagementComponent.getLatestImmutableState(reason);
        return new AutoCloseableWrapper<>(
                wrapper.isNull() ? null : (T) wrapper.get().getState().getSwirldState(), wrapper::close);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState(@NonNull final String reason) {
        final ReservedSignedState wrapper = stateManagementComponent.getLatestSignedState(reason);
        return new AutoCloseableWrapper<>(
                wrapper.isNull() ? null : (T) wrapper.get().getState().getSwirldState(), wrapper::close);
    }

    /**
     * @return the instance for calculating runningHash and writing event stream files
     */
    EventStreamManager<EventImpl> getEventStreamManager() {
        return eventStreamManager;
    }

    /**
     * get the StartUpEventFrozenManager used by this platform
     *
     * @return The StartUpEventFrozenManager used by this platform
     */
    StartUpEventFrozenManager getStartUpEventFrozenManager() {
        return startUpEventFrozenManager;
    }

    /**
     * check whether the given event is the last event in its round, and the platform enters freeze period
     *
     * @param event a consensus event
     * @return whether this event is the last event to be added before restart
     */
    private boolean isLastEventBeforeRestart(final EventImpl event) {
        return event.isLastInRoundReceived() && swirldStateManager.isInFreezePeriod(event.getConsensusTimestamp());
    }

    /**
     * @return the platform instance of the {@link ShadowGraphSynchronizer} used for sync
     */
    public ShadowGraphSynchronizer getSyncShadowGraphSynchronizer() {
        return syncShadowgraphSynchronizer;
    }

    /**
     * @return the instance that throttles simultaneous syncs
     */
    public SimultaneousSyncThrottle getSimultaneousSyncThrottle() {
        return simultaneousSyncThrottle;
    }
}
