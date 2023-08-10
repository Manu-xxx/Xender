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

import com.swirlds.common.StartupTime;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SystemExitUtils;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.gui.internal.InfoApp;
import com.swirlds.platform.gui.internal.InfoMember;
import com.swirlds.platform.gui.internal.InfoSwirld;
import com.swirlds.platform.gui.internal.StateHierarchy;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.startup.CommandLineArgs;
import com.swirlds.platform.startup.Log4jSetup;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.swirlds.common.system.SystemExitUtils.exitSystem;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.addPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.moveBroswerWindowToFront;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.showBrowserWindow;
import static com.swirlds.platform.util.BootstrapUtils.*;

/**
 * The Browser that launches the Platforms that run the apps.
 */
public class Browser {
    // Each member is represented by an AddressBook entry in config.txt. On a given computer, a single java
    // process runs all members whose listed internal IP address matches some address on that computer. That
    // Java process will instantiate one Platform per member running on that machine. But there will be only
    // one static Browser that they all share.
    //
    // Every member, whatever computer it is running on, listens on 0.0.0.0, on its internal port. Every
    // member connects to every other member, by computing its IP address as follows: If that other member
    // is also on the same host, use 127.0.0.1. If it is on the same LAN[*], use its internal address.
    // Otherwise, use its external address.
    //
    // This way, a single config.txt can be shared across computers unchanged, even if, for example, those
    // computers are on different networks in Amazon EC2.
    //
    // [*] Two members are considered to be on the same LAN if their listed external addresses are the same.

    private static Logger logger = LogManager.getLogger(Browser.class);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    public static void main(final String[] args) {
        parseCommandLineArgsAndLaunch(args);
    }

    /**
     * Start the browser running, if it isn't already running. If it's already running, then Browser.main does nothing.
     * Normally, an app calling Browser.main has no effect, because it was the browser that launched the app in the
     * first place, so the browser is already running.
     * <p>
     * But during app development, it can be convenient to give the app a main method that calls Browser.main. If there
     * is a config.txt file that says to run the app that is being developed, then the developer can run the app within
     * Eclipse. Eclipse will call the app's main() method, which will call the browser's main() method, which launches
     * the browser. The app's main() then returns, and the app stops running. Then the browser will load the app
     * (because of the config.txt file) and let it run normally within the browser. All of this happens within Eclipse,
     * so the Eclipse debugger works, and Eclipse breakpoints within the app will work.
     *
     * @param args args is ignored, and has no effect
     */
    public static void parseCommandLineArgsAndLaunch(final String... args) {
        if (STARTED.getAndSet(true)) {
            return;
        }

        final CommandLineArgs commandLineArgs = CommandLineArgs.parse(args);

        launch(commandLineArgs, ConfigurationHolder.getConfigData(PathsConfig.class).getLogPath());
    }

    /**
     * Launch the browser.
     *
     * @param log4jPath         the path to the log4j configuraiton file, if null then log4j is not started
     */
    public static void launch(final CommandLineArgs commandLineArgs, final Path log4jPath) {
        if (STARTED.getAndSet(true)) {
            return;
        }
        Log4jSetup.startLoggingFramework(log4jPath);
        logger = LogManager.getLogger(Browser.class);
        try {
           launch(commandLineArgs);
        } catch (Exception e) {
            logger.error(EXCEPTION.getMarker(), "", e);
            throw new RuntimeException("Unable to create Browser", e);
        }
    }

    /**
     * Prevent this class from being instantiated.
     */
    private static void launch(@NonNull final CommandLineArgs commandLineArgs) throws Exception {
        if (STARTED.getAndSet(true)) {
            return;
        }
        StartupTime.markStartupTime();
        Objects.requireNonNull(commandLineArgs, "localNodesToStart must not be null");
        logger.info(STARTUP.getMarker(), "\n\n" + STARTUP_MESSAGE + "\n");
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        final PathsConfig pathsConfig = loadPathsConfig();

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final ApplicationDefinition appDefinition = ApplicationDefinitionLoader.loadDefault(pathsConfig.getConfigPath());
        // Determine which nodes to run locally
        final List<NodeId> nodesToRun = getNodesToRun(appDefinition.getConfigAddressBook(), commandLineArgs.localNodesToStart());
        checkNodesToRun(nodesToRun);
        // Load all SwirldMain instances for locally run nodes.
        final Map<NodeId, SwirldMain> appMains = loadSwirldMains(appDefinition, nodesToRun);
        ParameterProvider.getInstance().setParameters(appDefinition.getAppParameters());
        Configuration configuration = BootstrapUtils.loadConfig(pathsConfig, appMains);
        PlatformConfigUtils.checkConfiguration(configuration);

        ConfigurationHolder.getInstance().setConfiguration(configuration);
        CryptographyHolder.reset();

        BootstrapUtils.performHealthChecks(configuration);

        setupBrowserWindow();
        // Write the settingsUsed.txt file
        writeSettingsUsed(configuration);
        // find all the apps in data/apps and stored states in data/states
        setStateHierarchy(new StateHierarchy(null));

        // instantiate all Platform objects, which each instantiates a Statistics object
        logger.debug(STARTUP.getMarker(), "About to run startPlatforms()");

        final AddressBook configAddressBook = appDefinition.getConfigAddressBook();

        // If enabled, clean out the signed state directory. Needs to be done before the platform/state is started up,
        // as we don't want to delete the temporary file directory if it ends up being put in the saved state directory.
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final String mainClassName = stateConfig.getMainClassName(appDefinition.getMainClassName());
        if (stateConfig.cleanSavedStateDirectory()) {
            SignedStateFileUtils.cleanStateDirectory(mainClassName);
        }

        appDefinition.setSwirldId(new byte[CryptoConstants.HASH_SIZE_BYTES]);

        // Create the various keys and certificates (which are saved in various Crypto objects).
        // Save the certificates in the trust stores.
        // Save the trust stores in the address book.
        logger.debug(STARTUP.getMarker(), "About do crypto instantiation");
        final Map<NodeId, Crypto> crypto = initNodeSecurity(appDefinition.getConfigAddressBook(), configuration);
        logger.debug(STARTUP.getMarker(), "Done with crypto instantiation");

        // the AddressBook is not changed after this point, so we calculate the hash now
        CryptographyHolder.get().digestSync(configAddressBook);

        final InfoApp infoApp = getStateHierarchy().getInfoApp(appDefinition.getApplicationName());
        final InfoSwirld infoSwirld = new InfoSwirld(infoApp, appDefinition.getSwirldId());

        logger.debug(STARTUP.getMarker(), "Starting platforms");

        // Setup metrics system
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        final Map<NodeId, SwirldsPlatform> platforms = new HashMap<>();

        for (final NodeId nodeId : nodesToRun) {
            platforms.put(
                    nodeId,
                    buildPlatform(
                        nodeId,
                        appDefinition,
                        configAddressBook,
                        appMains.get(nodeId),
                        metricsProvider,
                        configuration,
                        infoSwirld,
                        crypto.get(nodeId)
            ));
        }

        addPlatforms(platforms.values());

        // init appMains
        for (final NodeId nodeId : nodesToRun) {
            appMains.get(nodeId).init(platforms.get(nodeId), nodeId);
        }

        // build app threads
        List<Thread> appRunThreads = new ArrayList<>();
        for (final NodeId nodeId : nodesToRun) {
            final Thread appThread = new ThreadConfiguration(getStaticThreadManager())
                    .setNodeId(nodeId)
                    .setComponent("app")
                    .setThreadName("appMain")
                    .setRunnable(appMains.get(nodeId))
                    .build();
            // IMPORTANT: this swirlds app thread must be non-daemon,
            // so that the JVM will not exit when the main thread exits
            appThread.setDaemon(false);
            appRunThreads.add(appThread);
        }

        // Write all metrics information to file
        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);

        platforms.values().forEach(SwirldsPlatform::start);
        appRunThreads.forEach(Thread::start);

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator(configuration);

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread(configuration);

        logger.info(STARTUP.getMarker(), "Starting metrics");
        metricsProvider.start();

        logger.debug(STARTUP.getMarker(), "Done with starting platforms");

        // create the browser window, which uses those Statistics objects
        showBrowserWindow();
        moveBroswerWindowToFront();

        CommonUtils.tellUserConsole(
                "This computer has an internal IP address:  " + Network.getInternalIPAddress());
        logger.trace(
                STARTUP.getMarker(),
                "All of this computer's addresses: {}",
                () -> (Arrays.toString(Network.getOwnAddresses2())));


        logger.debug(STARTUP.getMarker(), "main() finished");
    }


    private static SwirldsPlatform buildPlatform(
            final NodeId nodeId,
            final ApplicationDefinition appDefinition,
            final AddressBook configAddressBook,
            final SwirldMain appMain,
            final MetricsProvider metricsProvider,
            final Configuration configuration,
            final InfoSwirld infoSwirld,
            final Crypto crypto) throws IOException {
        final Address address = configAddressBook.getAddress(nodeId);
        final int instanceNumber = configAddressBook.getIndexOfNodeId(nodeId);

        // this is a node to start locally.
        final String platformName = address.getNickname()
                + " - " + address.getSelfName()
                + " - " + infoSwirld.getName()
                + " - " + infoSwirld.getApp().getName();

        final PlatformContext platformContext =
                new DefaultPlatformContext(nodeId, metricsProvider, configuration);

        // name of the app's SwirldMain class
        final String mainClassName = appDefinition.getMainClassName();
        // the name of this swirld
        final String swirldName = appDefinition.getSwirldName();
        final SoftwareVersion appVersion = appMain.getSoftwareVersion();

        final RecycleBin recycleBin = RecycleBin.create(configuration, nodeId);

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                stateConfig, SystemExitUtils::exitSystem, basicConfig.getEmergencyRecoveryFileLoadDir());

        final ReservedSignedState initialState = getInitialState(
                platformContext,
                appMain,
                mainClassName,
                swirldName,
                nodeId,
                configAddressBook,
                emergencyRecoveryManager);

        try (initialState) {
            // check software version compatibility
            final boolean softwareUpgrade = detectSoftwareUpgrade(appVersion, initialState.get());

            if (softwareUpgrade) {
                logger.info(STARTUP.getMarker(), "Clearing recycle bin as part of software upgrade workflow.");
                recycleBin.clear();
            }

            // Initialize the address book from the configuration and platform saved state.
            final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                    appVersion, softwareUpgrade, initialState.get(), configAddressBook.copy(), platformContext);

            if (!initialState.get().isGenesisState()) {
                updateLoadedStateAddressBook(
                        initialState.get(), addressBookInitializer.getInitialAddressBook());
            }

            GuiPlatformAccessor.getInstance().setPlatformName(nodeId, platformName);
            GuiPlatformAccessor.getInstance().setSwirldId(nodeId, appDefinition.getSwirldId());
            GuiPlatformAccessor.getInstance().setInstanceNumber(nodeId, instanceNumber);

            final SwirldsPlatform platform = new SwirldsPlatform(
                    platformContext,
                    crypto,
                    recycleBin,
                    nodeId,
                    mainClassName,
                    swirldName,
                    appVersion,
                    initialState.get(),
                    emergencyRecoveryManager);
            new InfoMember(infoSwirld, platform);
            return platform;
        }

    }

}
