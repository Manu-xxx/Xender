/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.addressbook;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * An application that updates address book weights on version upgrade.
 * </p>
 *
 * <p>
 * Arguments:
 * <ol>
 * <li>
 * No arguments parsed at this time.  The software version must be updated through setting the static value in
 * this main class and recompiling. The behavior of weighting is updated in the State class and recompiling.
 * </li>
 * </ol>
 */
public class AddressBookTestingToolMain implements SwirldMain {
    /** The logger for this class. */
    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolMain.class);

    /** The software version of this application. */
    private BasicSoftwareVersion softwareVersion;

    /** The platform. */
    private Platform platform;

    /** The number of transactions to generate per second. */
    private static final int TRANSACTIONS_PER_SECOND = 1000;

    public AddressBookTestingToolMain() {
        logger.info(STARTUP.getMarker(), "constructor called in Main.");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public List<Class<? extends Record>> getConfigDataTypes() {
        return List.of(AddressBookTestingToolConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId id) {
        Objects.requireNonNull(platform, "The platform must not be null.");
        Objects.requireNonNull(id, "The node id must not be null.");

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", id);
        this.platform = platform;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        logger.info(STARTUP.getMarker(), "run called in Main.");
        new TransactionGenerator(new SecureRandom(), platform, TRANSACTIONS_PER_SECOND).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SwirldState newState() {
        return new AddressBookTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public BasicSoftwareVersion getSoftwareVersion() {
        if (softwareVersion != null) {
            return softwareVersion;
        }

        // Preload configuration so that we can change the software version on the fly
        final ConfigurationBuilder configurationBuilder;
        try {
            // TODO this will probably log like crazy
            //  note to the reviewers: I am currently testing this and will delete this prior to merge
            configurationBuilder = ConfigurationBuilder.create()
                    .withConfigDataType(AddressBookTestingToolConfig.class)
                    .withSource(LegacyFileConfigSource.ofSettingsFile());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to load settings.txt", e);
        }

        final Configuration configuration = configurationBuilder.build();

        final int version =
                configuration.getConfigData(AddressBookTestingToolConfig.class).softwareVersion();
        this.softwareVersion = new BasicSoftwareVersion(version);

        logger.info(STARTUP.getMarker(), "returning software version {}", softwareVersion);
        return softwareVersion;
    }
}
