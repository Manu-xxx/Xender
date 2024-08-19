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

package com.hedera.node.app;

import static com.hedera.node.app.info.UnavailableNetworkInfo.UNAVAILABLE_NETWORK_INFO;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.MOD_POST_EVENT_STREAM_REPLAY;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.selectedDumpCheckpoints;
import static com.hedera.node.app.statedumpers.StateDumper.dumpModChildrenFrom;
import static com.hedera.node.app.util.HederaAsciiArt.HEDERA;
import static com.hedera.node.app.workflows.handle.metric.UnavailableMetrics.UNAVAILABLE_METRICS;
import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.status.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.CurrentPlatformStatusImpl;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.info.UnavailableLedgerIdNetworkInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.state.MerkleStateLifecyclesImpl;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.MerkleStateChild;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.config.Utils;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.State;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 ****************        ****************************************************************************************
 ************                ************                                                                       *
 *********                      *********                                                                       *
 ******                            ******                                                                       *
 ****                                ****      ___           ___           ___           ___           ___      *
 ***        ĦĦĦĦ          ĦĦĦĦ        ***     /\  \         /\  \         /\  \         /\  \         /\  \     *
 **         ĦĦĦĦ          ĦĦĦĦ         **    /::\  \       /::\  \       /::\  \       /::\  \       /::\  \    *
 *          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *   /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \   *
            ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ             /::\~\:\  \   /:/  \:\__\   /::\~\:\  \   /::\~\:\  \   /::\~\:\  \  *
            ĦĦĦĦ          ĦĦĦĦ            /:/\:\ \:\__\ /:/__/ \:|__| /:/\:\ \:\__\ /:/\:\ \:\__\ /:/\:\ \:\__\ *
            ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ            \:\~\:\ \/__/ \:\  \ /:/  / \:\~\:\ \/__/ \/_|::\/:/  / \/__\:\/:/  / *
 *          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *  \:\ \:\__\    \:\  /:/  /   \:\ \:\__\      |:|::/  /       \::/  /  *
 **         ĦĦĦĦ          ĦĦĦĦ         **   \:\ \/__/     \:\/:/  /     \:\ \/__/      |:|\/__/        /:/  /   *
 ***        ĦĦĦĦ          ĦĦĦĦ        ***    \:\__\        \::/__/       \:\__\        |:|  |         /:/  /    *
 ****                                ****     \/__/         ~~            \/__/         \|__|         \/__/     *
 ******                            ******                                                                       *
 *********                      *********                                                                       *
 ************                ************                                                                       *
 ****************        ****************************************************************************************
*/

/**
 * Represents the Hedera Consensus Node.
 *
 * <p>This is the main entry point for the Hedera Consensus Node. It contains initialization logic for the node,
 * including its state. It constructs the Dagger dependency tree, and manages the gRPC server, and in all other ways,
 * controls execution of the node. If you want to understand our system, this is a great place to start!
 */
public final class Hedera implements SwirldMain, PlatformStatusChangeListener {
    private static final Logger logger = LogManager.getLogger(Hedera.class);

    // FUTURE: This should come from configuration, not be hardcoded.
    public static final int MAX_SIGNED_TXN_SIZE = 6144;

    /**
     * The application name from the platform's perspective. This is currently locked in at the old main class name and
     * requires data migration to change.
     */
    public static final String APP_NAME = "com.hedera.services.ServicesMain";

    /**
     * The swirld name. Currently, there is only one swirld.
     */
    public static final String SWIRLD_NAME = "123";
    /**
     * The registry to use.
     */
    private final ServicesRegistry servicesRegistry;
    /**
     * The services migrator to use.
     */
    private final ServiceMigrator serviceMigrator;
    /**
     * The current version of the software; it is not for a node's version to change
     * without restarting the process, so final.
     */
    private final HederaSoftwareVersion version;

    /**
     * The source of time the node should use for screening transactions at ingest.
     */
    private final InstantSource instantSource;

    /**
     * The contract service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final ContractServiceImpl contractServiceImpl;

    /**
     * The file service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final FileServiceImpl fileServiceImpl;

    /**
     * The bootstrap configuration provider for the network.
     */
    private final BootstrapConfigProviderImpl bootstrapConfigProvider;

    /**
     * The Hashgraph Platform. This is set during state initialization.
     */
    private Platform platform;
    /**
     * The configuration for this node; non-final because its sources depend on whether
     * we are initializing the first consensus state from genesis or a saved state.
     */
    private ConfigProviderImpl configProvider;
    /**
     * DI for all objects needed to implement Hedera node lifecycles; non-final because
     * it is completely recreated every time the platform initializes a new state as the
     * basis for applying consensus transactions.
     */
    private HederaInjectionComponent daggerApp;
    /**
     * The latest platform status we have received via notification.
     */
    private PlatformStatus platformStatus = STARTING_UP;

    private Metrics metrics;

    /**
     * A factory object for creating {@link SoftwareVersion} based on provided {@link SemanticVersion};
     * needed only until we remove the HAPI version from {@link HederaSoftwareVersion} (the node
     * version alone encompasses the protobuf artifacts compiled for its deployment, so the platform
     * doesn't need the extra detail of the HAPI version).
     */
    private final Function<SemanticVersion, SoftwareVersion> versionFactory;

    /*==================================================================================================================
    *
    * Hedera Object Construction.
    *
    =================================================================================================================*/

    /**
     * Creates a Hedera node and registers its own and its services' {@link RuntimeConstructable} factories
     * with the given {@link ConstructableRegistry}.
     *
     * <p>This registration is a critical side effect that must happen called before any Platform initialization
     * steps that try to create or deserialize a {@link MerkleStateRoot}.
     *
     * @param constructableRegistry the registry to register {@link RuntimeConstructable} factories with
     * @param registryFactory the factory to use for creating the services registry
     * @param migrator the migrator to use with the services
     */
    public Hedera(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final ServicesRegistry.Factory registryFactory,
            @NonNull final ServiceMigrator migrator,
            @NonNull final InstantSource instantSource) {
        requireNonNull(registryFactory);
        requireNonNull(constructableRegistry);
        this.serviceMigrator = requireNonNull(migrator);
        this.instantSource = requireNonNull(instantSource);
        logger.info(
                """

                        {}

                        Welcome to Hedera! Developed with ❤\uFE0F by the Open Source Community.
                        https://github.com/hashgraph/hedera-services

                        """,
                HEDERA);
        bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final Configuration bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        version = getNodeStartupVersion(bootstrapConfig);
        servicesRegistry = registryFactory.create(constructableRegistry, bootstrapConfig);
        logger.info(
                "Creating Hedera Consensus Node {} with HAPI {}",
                version::readableServicesVersion,
                () -> HapiUtils.toString(version.getHapiVersion()));
        fileServiceImpl = new FileServiceImpl();
        versionFactory = v -> new HederaSoftwareVersion(version.getHapiVersion(), v);

        final var appContext = new AppContextImpl(
                instantSource,
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())));
        contractServiceImpl = new ContractServiceImpl(appContext);
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        contractServiceImpl,
                        fileServiceImpl,
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl(),
                        PLATFORM_STATE_SERVICE)
                .forEach(servicesRegistry::register);
        try {
            // And the factory for the MerkleStateRoot class id must be our constructor
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    MerkleStateRoot.class,
                    () -> new MerkleStateRoot(new MerkleStateLifecyclesImpl(this), versionFactory)));
        } catch (final ConstructableRegistryException e) {
            logger.error("Failed to register " + MerkleStateRoot.class + " factory with ConstructableRegistry", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called immediately after the constructor to get the version of this software. In an upgrade scenario, this
     * version will be greater than the one in the saved state.
     *
     * @return The software version.
     */
    @Override
    @NonNull
    public SoftwareVersion getSoftwareVersion() {
        return version;
    }

    /*==================================================================================================================
    *
    * Initialization Step 1: Create a new state (either genesis or restart, once per node).
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called by the platform to either build a genesis state.
     *
     * @return a Services state object
     */
    @Override
    @NonNull
    public MerkleRoot newMerkleStateRoot() {
        return new MerkleStateRoot(new MerkleStateLifecyclesImpl(this), versionFactory);
    }

    @Override
    public void notify(@NonNull final PlatformStatusChangeNotification notification) {
        platformStatus = notification.getNewStatus();
        logger.info("HederaNode#{} is {}", platform.getSelfId(), platformStatus.name());
        switch (platformStatus) {
            case ACTIVE -> startGrpcServer();
            case CATASTROPHIC_FAILURE -> shutdownGrpcServer();
            case FREEZE_COMPLETE -> {
                closeRecordStreams();
                shutdownGrpcServer();
            }
            case REPLAYING_EVENTS, STARTING_UP, OBSERVING, RECONNECT_COMPLETE, CHECKING, FREEZING, BEHIND -> {
                // Nothing to do here, just enumerate for completeness
            }
        }
    }

    /*==================================================================================================================
    *
    * Initialization Step 2: Initialize the state. Either genesis or restart or reconnect or some other trigger.
    * Includes migration when needed.
    *
    =================================================================================================================*/

    /**
     * Invoked by {@link MerkleStateRoot} when it needs to ensure the {@link PlatformStateService} is initialized.
     * @param root the root state to be initialized
     */
    public void initPlatformState(@NonNull final MerkleStateRoot root) {
        requireNonNull(root);
        serviceMigrator.doMigrations(
                root,
                servicesRegistry.subRegistryFor(EntityIdService.NAME, PlatformStateService.NAME),
                PLATFORM_STATE_SERVICE.creationVersionOf(root),
                version.getPbjSemanticVersion(),
                bootstrapConfigProvider.getConfiguration(),
                UNAVAILABLE_NETWORK_INFO,
                UNAVAILABLE_METRICS);
    }

    /**
     * Invoked by the platform when the state should be initialized. This happens <b>BEFORE</b>
     * {@link #init(Platform, NodeId)} and after {@link #newMerkleStateRoot()}.
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    public void onStateInitialized(
            @NonNull final State state,
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousVersion) {
        // A Hedera object can receive multiple onStateInitialized() calls throughout its lifetime if
        // the platform needs to initialize a learned state after reconnect; however, it cannot be
        // used by multiple platform instances
        if (this.platform != null && this.platform != platform) {
            logger.fatal("Fatal error, platform should never change once set");
            throw new IllegalStateException("Platform should never change once set");
        }
        this.platform = requireNonNull(platform);
        this.metrics = platform.getContext().getMetrics();
        this.configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        logger.info(
                "Initializing Hedera state version {} in {} mode with trigger {} and previous version {}",
                version,
                configProvider
                        .getConfiguration()
                        .getConfigData(HederaConfig.class)
                        .activeProfile(),
                trigger,
                previousVersion == null ? "<NONE>" : previousVersion);
        final var readableStore = new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
        logger.info(
                "Platform state includes freeze time={} and last frozen={}",
                readableStore.getFreezeTime(),
                readableStore.getLastFrozenTime());

        HederaSoftwareVersion deserializedVersion = null;
        // We do not support downgrading from one version to an older version.
        if (previousVersion instanceof HederaSoftwareVersion hederaSoftwareVersion) {
            deserializedVersion = hederaSoftwareVersion;
            if (version.isBefore(deserializedVersion)) {
                logger.fatal(
                        "Fatal error, state source version {} is higher than node software version {}",
                        deserializedVersion,
                        version);
                throw new IllegalStateException("Cannot downgrade from " + deserializedVersion + " to " + version);
            }
        } else {
            if (previousVersion != null) {
                logger.fatal("Deserialized state not created with Hedera software");
                throw new IllegalStateException("Deserialized state not created with Hedera software");
            }
        }
        try {
            migrateAndInitialize(state, deserializedVersion, trigger, metrics);
        } catch (final Throwable t) {
            logger.fatal("Critical failure during initialization", t);
            throw new IllegalStateException("Critical failure during initialization", t);
        }
    }

    /**
     * Called by this class when we detect it is time to do migration. The {@code deserializedVersion} must not be newer
     * than the current software version. If it is prior to the current version, then each migration between the
     * {@code deserializedVersion} and the current version, including the current version, will be executed, thus
     * bringing the state up to date.
     *
     * <p>If the {@code deserializedVersion} is {@code null}, then this is the first time the node has been started,
     * and thus all schemas will be executed.
     *
     * @param state current state
     * @param deserializedVersion version deserialized
     * @param trigger trigger that is calling migration
     */
    private void onMigrate(
            @NonNull final State state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics) {
        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getPbjSemanticVersion();
        final var currentVersion = version.getPbjSemanticVersion();
        final var isUpgrade = isSoOrdered(previousVersion, currentVersion);
        logger.info(
                "{} from Services version {} @ current {} with trigger {}",
                () -> isUpgrade ? "Upgrading" : (previousVersion == null ? "Starting" : "Restarting"),
                () -> HapiUtils.toString(previousVersion),
                version::readableServicesVersion,
                () -> trigger);
        final var selfNodeInfo = extractSelfNodeInfo(platform, version);
        final var networkInfo = new UnavailableLedgerIdNetworkInfo(selfNodeInfo, platform);
        // (FUTURE) In principle, the FileService could actually change the active configuration during a
        // migration, which implies we should be passing the config provider and not a static configuration
        // here; but this is a currently unneeded affordance
        serviceMigrator.doMigrations(
                state,
                servicesRegistry,
                previousVersion,
                currentVersion,
                configProvider.getConfiguration(),
                networkInfo,
                metrics);
        if (isUpgrade && !trigger.equals(RECONNECT)) {
            unmarkMigrationRecordsStreamed(state);
        }
        logger.info("Migration complete");
    }

    /*==================================================================================================================
    *
    * Initialization Step 3: Initialize the app. Happens once at startup.
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called <b>AFTER</b> init and migrate have been called on the state (either the new state created from
     * {@link #newMerkleStateRoot()} or an instance of {@link MerkleStateRoot} created by the platform and
     * loaded from the saved state).
     *
     * <p>(FUTURE) Consider moving this initialization into {@link #onStateInitialized(State, Platform, InitTrigger, SoftwareVersion)}
     * instead, as there is no special significance to having it here instead.
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        if (this.platform != platform) {
            throw new IllegalArgumentException("Platform must be the same instance");
        }
        assertEnvSanityChecks(nodeId);
        logger.info("Initializing Hedera app with HederaNode#{}", nodeId);
        Locale.setDefault(Locale.US);
        logger.info("Locale to set to US en");
    }

    /**
     * Called to perform orderly close record streams.
     */
    private void closeRecordStreams() {
        daggerApp.blockRecordManager().close();
    }

    /**
     * Gets whether the default charset is UTF-8.
     */
    private boolean isUTF8(@NonNull final Charset defaultCharset) {
        if (!UTF_8.equals(defaultCharset)) {
            logger.error("Default charset is {}, not UTF-8", defaultCharset);
            return false;
        }
        return true;
    }

    /**
     * Gets whether the sha384 digest is available
     */
    private boolean sha384DigestIsAvailable() {
        try {
            MessageDigest.getInstance("SHA-384");
            return true;
        } catch (final NoSuchAlgorithmException e) {
            logger.error(e);
            return false;
        }
    }

    /*==================================================================================================================
    *
    * Other app lifecycle methods
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called by the platform after <b>ALL</b> initialization to start the gRPC servers and begin operation, or by
     * the notification listener when it is time to restart the gRPC server after it had been stopped (such as during
     * reconnect).
     */
    @Override
    public void run() {
        logger.info("Starting the Hedera node");
    }

    /**
     * Called for an orderly shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down Hedera node");
        shutdownGrpcServer();

        if (daggerApp != null) {
            logger.debug("Shutting down the state");
            final var state = daggerApp.workingStateAccessor().getState();
            if (state instanceof MerkleStateRoot msr) {
                msr.close();
            }

            logger.debug("Shutting down the block manager");
            daggerApp.blockRecordManager().close();
        }

        platform = null;
        daggerApp = null;
    }

    /**
     * Invoked by the platform to handle pre-consensus events. This only happens after {@link #run()} has been called.
     */
    public void onPreHandle(@NonNull final Event event, @NonNull final State state) {
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var creator =
                daggerApp.networkInfo().nodeInfo(event.getCreatorId().id());
        if (creator == null) {
            // It's normal immediately post-upgrade to still see events from a node removed from the address book
            if (!isSoOrdered(event.getSoftwareVersion(), version.getPbjSemanticVersion())) {
                logger.warn(
                        "Received event (version {} vs current {}) from node {} which is not in the address book",
                        com.hedera.hapi.util.HapiUtils.toString(event.getSoftwareVersion()),
                        com.hedera.hapi.util.HapiUtils.toString(version.getPbjSemanticVersion()),
                        event.getCreatorId());
            }
            return;
        }

        final var transactions = new ArrayList<Transaction>(1000);
        event.forEachTransaction(transactions::add);
        daggerApp.preHandleWorkflow().preHandle(readableStoreFactory, creator.accountId(), transactions.stream());
    }

    public void onNewRecoveredState(@NonNull final MerkleStateRoot recoveredState) {
        try {
            if (shouldDump(daggerApp.initTrigger(), MOD_POST_EVENT_STREAM_REPLAY)) {
                dumpModChildrenFrom(recoveredState, MOD_POST_EVENT_STREAM_REPLAY, MerkleStateChild.childrenToDump());
            }
        } catch (Exception e) {
            logger.error("Error dumping state after migration at MOD_POST_EVENT_STREAM_REPLAY", e);
        }
        // Always close the block manager so replay will end with a complete record file
        daggerApp.blockRecordManager().close();
    }

    public static boolean shouldDump(@NonNull final InitTrigger trigger, @NonNull final DumpCheckpoint checkpoint) {
        return trigger == EVENT_STREAM_RECOVERY && selectedDumpCheckpoints().contains(checkpoint);
    }

    /**
     * Invoked by the platform to handle a round of consensus events.  This only happens after {@link #run()} has been
     * called.
     */
    public void onHandleConsensusRound(@NonNull final Round round, @NonNull final State state) {
        daggerApp.workingStateAccessor().setState(state);
        daggerApp.handleWorkflow().handleRound(state, round);
    }

    /*==================================================================================================================
    *
    * gRPC Server Lifecycle
    *
    =================================================================================================================*/

    /**
     * Start the gRPC Server if it is not already running.
     */
    void startGrpcServer() {
        if (isNotEmbedded() && !daggerApp.grpcServerManager().isRunning()) {
            daggerApp.grpcServerManager().start();
        }
    }

    /**
     * Called to perform orderly shutdown of the gRPC servers.
     */
    public void shutdownGrpcServer() {
        if (isNotEmbedded()) {
            daggerApp.grpcServerManager().stop();
        }
    }

    /*==================================================================================================================
    *
    * Workflows for use by embedded Hedera
    *
    =================================================================================================================*/
    public IngestWorkflow ingestWorkflow() {
        return daggerApp.ingestWorkflow();
    }

    public QueryWorkflow queryWorkflow() {
        return daggerApp.queryWorkflow();
    }

    public HandleWorkflow handleWorkflow() {
        return daggerApp.handleWorkflow();
    }

    /*==================================================================================================================
    *
    * Genesis Initialization
    *
    =================================================================================================================*/

    private void migrateAndInitialize(
            @NonNull final State state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics) {
        if (trigger != GENESIS) {
            requireNonNull(deserializedVersion, "Deserialized version cannot be null for trigger " + trigger);
        }
        // Until all service schemas are migrated, MerkleStateRoot will not be able to implement
        // the States API, even if it already has all its children in the Merkle tree, as it will lack
        // state definitions for those children. (And note services may even require migrations for
        // those children to be usable with the current version of the software.)
        onMigrate(state, deserializedVersion, trigger, metrics);
        // With the States API grounded in the working state, we can create the object graph from it
        initializeDagger(state, trigger);
        // Log the active configuration
        logConfiguration();
    }

    /*==================================================================================================================
    *
    * Random private helper methods
    *
    =================================================================================================================*/

    private void initializeDagger(@NonNull final State state, @NonNull final InitTrigger trigger) {
        final var notifications = platform.getNotificationEngine();
        // The Dagger component should be constructed every time we reach this point, even if
        // it exists (this avoids any problems with mutable singleton state by reconstructing
        // everything); but we must ensure the gRPC server in the old component is fully stopped,
        // as well as unregister listeners from the last time this method ran
        if (daggerApp != null) {
            shutdownGrpcServer();
            notifications.unregister(PlatformStatusChangeListener.class, this);
            notifications.unregister(ReconnectCompleteListener.class, daggerApp.reconnectListener());
            notifications.unregister(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
        }
        // Fully qualified so as to not confuse javadoc
        daggerApp = com.hedera.node.app.DaggerHederaInjectionComponent.builder()
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .fileServiceImpl(fileServiceImpl)
                .contractServiceImpl(contractServiceImpl)
                .initTrigger(trigger)
                .softwareVersion(version.getPbjSemanticVersion())
                .self(extractSelfNodeInfo(platform, version))
                .platform(platform)
                .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                .crypto(CryptographyHolder.get())
                .currentPlatformStatus(new CurrentPlatformStatusImpl(platform))
                .servicesRegistry(servicesRegistry)
                .instantSource(instantSource)
                .metrics(metrics)
                .build();
        // Initialize infrastructure for fees, exchange rates, and throttles from the working state
        daggerApp.initializer().accept(state);
        notifications.register(PlatformStatusChangeListener.class, this);
        notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
        notifications.register(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
    }

    private static HederaSoftwareVersion getNodeStartupVersion(@NonNull final Configuration config) {
        final var versionConfig = config.getConfigData(VersionConfig.class);
        return new HederaSoftwareVersion(
                versionConfig.hapiVersion(),
                versionConfig.servicesVersion(),
                config.getConfigData(HederaConfig.class).configVersion());
    }

    private void logConfiguration() {
        if (logger.isInfoEnabled()) {
            final var config = configProvider.getConfiguration();
            final var lines = new ArrayList<String>();
            lines.add("Active Configuration:");
            Utils.allProperties(config).forEach((key, value) -> lines.add(key + " = " + value));
            logger.info(String.join("\n", lines));
        }
    }

    private void unmarkMigrationRecordsStreamed(@NonNull final State state) {
        final var blockServiceState = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = blockServiceState.<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY);
        final var currentBlockInfo = requireNonNull(blockInfoState.get());
        final var nextBlockInfo =
                currentBlockInfo.copyBuilder().migrationRecordsStreamed(false).build();
        blockInfoState.put(nextBlockInfo);
        logger.info("Unmarked post-upgrade work as done");
        ((WritableSingletonStateBase<BlockInfo>) blockInfoState).commit();
    }

    private void assertEnvSanityChecks(@NonNull final NodeId nodeId) {
        // Check that UTF-8 is in use. Otherwise, the node will be subject to subtle bugs in string handling that will
        // lead to ISS.
        final var defaultCharset = daggerApp.nativeCharset().get();
        if (!isUTF8(defaultCharset)) {
            logger.error(
                    """
                            Fatal precondition violation in HederaNode#{}: default charset is {} and not UTF-8
                            LC_ALL={}
                            LANG={}
                            file.encoding={}
                            """,
                    nodeId,
                    defaultCharset,
                    System.getenv("LC_ALL"),
                    System.getenv("LANG"),
                    System.getProperty("file.encoding"));
            System.exit(1);
        }

        // Check that the digest factory supports SHA-384.
        if (!sha384DigestIsAvailable()) {
            logger.error(
                    "Fatal precondition violation in HederaNode#{}: digest factory does not support SHA-384", nodeId);
            System.exit(1);
        }
    }

    private SelfNodeInfo extractSelfNodeInfo(
            @NonNull final Platform platform, @NonNull final HederaSoftwareVersion version) {
        final var selfId = platform.getSelfId();
        final var nodeAddress = platform.getAddressBook().getAddress(selfId);
        return SelfNodeInfoImpl.of(nodeAddress, version);
    }

    /**
     * Returns true if the source of time is the system time. Always true for live networks.
     *
     * @return true if the source of time is the system time
     */
    private boolean isNotEmbedded() {
        return instantSource == InstantSource.system();
    }
}
