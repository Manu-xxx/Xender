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

package com.hedera.node.app.config;

import static java.util.Objects.requireNonNull;

import com.hedera.node.config.ConfigProvider;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.PropertyFileConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.ObjIntConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A convenient base class for implementing configuration providers. Not intended to be used outside of this package.
 */
public abstract class ConfigProviderBase implements ConfigProvider {
    /**
     * Name of an environment variable that can be used to override the default path to the genesis.properties file (see
     * {@link #GENESIS_PROPERTIES_DEFAULT_PATH}).
     */
    public static final String GENESIS_PROPERTIES_PATH_ENV = "HEDERA_GENESIS_PROPERTIES_PATH";
    /**
     * Name of an environment variable that can be used to override the default path to the application.properties file
     * (see {@link #APPLICATION_PROPERTIES_DEFAULT_PATH}).
     */
    public static final String APPLICATION_PROPERTIES_PATH_ENV = "HEDERA_APP_PROPERTIES_PATH";
    /** Default path to the genesis.properties file. */
    public static final String GENESIS_PROPERTIES_DEFAULT_PATH = "genesis.properties";
    /** Default path to the application.properties file. */
    public static final String APPLICATION_PROPERTIES_DEFAULT_PATH = "application.properties";

    private static final Logger logger = LogManager.getLogger(ConfigProviderBase.class);
    /** Default path to the semantic-version.properties file. */
    protected static final String SEMANTIC_VERSION_PROPERTIES_DEFAULT_PATH = "semantic-version.properties";

    /**
     * Adds a file from which to read configuration information.
     *
     * @param builder The configuration builder to which the file source will be added.
     * @param envName The name of an environment variable that can be used to override the default path to the file.
     * @param defaultPath The default path to the file.
     * @param priority The priority of the file source in the overall configuration.
     */
    protected void addFileSource(
            @NonNull final ConfigurationBuilder builder,
            @NonNull final String envName,
            @NonNull final String defaultPath,
            final int priority) {
        requireNonNull(builder);
        requireNonNull(envName);
        requireNonNull(defaultPath);

        final ObjIntConsumer<Path> addSource = (path, p) -> {
            if (path.toFile().exists()) {
                if (!path.toFile().isDirectory()) {
                    try {
                        builder.withSource(new PropertyFileConfigSource(path, p));
                    } catch (IOException e) {
                        throw new IllegalStateException("Can not create config source for property file", e);
                    }
                } else {
                    throw new IllegalArgumentException("File " + path + " is a directory and not a property file");
                }
            } else {
                logger.warn("Properties file {} does not exist and won't be used as configuration source", path);
            }
        };

        try {
            final Path propertiesPath =
                    Optional.ofNullable(System.getenv(envName)).map(Path::of).orElseGet(() -> Path.of(defaultPath));
            addSource.accept(propertiesPath, priority);
        } catch (final Exception e) {
            throw new IllegalStateException("Can not create config source for application properties", e);
        }
    }
}
