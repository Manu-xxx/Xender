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

package com.swirlds.platform.config.legacy;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.Settings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loader that load all properties form the config.txt file
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future once the onfig.txt
 * 		has been migrated to the regular config API. If you need to use this class please try to do as less static
 * 		access as
 * 		possible.
 */
@Deprecated(forRemoval = true)
public final class LegacyConfigPropertiesLoader {

    private static final String APP_PROPERTY_NAME = "app";

    private static final String ADDRESS_PROPERTY_NAME = "address";

    private static final Logger logger = LogManager.getLogger(LegacyConfigPropertiesLoader.class);

    private LegacyConfigPropertiesLoader() {}

    public static LegacyConfigProperties loadConfigFile(Path configPath) throws ConfigurationException {
        CommonUtils.throwArgNull(configPath, "configPath");

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        if (!Files.exists(configPath)) {
            logger.error(EXCEPTION.getMarker(), "ERROR: Browser.startPlatforms called on non-existent config.txt");
            throw new ConfigurationException("ERROR: Browser.startPlatforms called on non-existent config.txt");
        }

        final LegacyConfigProperties configurationProperties = new LegacyConfigProperties();

        try (final Scanner scanner = new Scanner(configPath, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                final String line = readNextLine(scanner);
                if (!line.isEmpty()) {
                    final String[] lineParameters = Settings.splitLine(line);
                    final int len = Math.max(10, lineParameters.length);
                    // pars is the comma-separated parameters, trimmed, lower-cased, then padded with "" to have
                    // at least 10 parameters
                    final String[] pars = new String[len];
                    final String[] parsOriginalCase = new String[len];
                    for (int i = 0; i < len; i++) {
                        parsOriginalCase[i] = i >= lineParameters.length ? "" : lineParameters[i].trim();
                        pars[i] = parsOriginalCase[i].toLowerCase(Locale.ENGLISH);
                    }
                    switch (pars[0]) {
                        case APP_PROPERTY_NAME:
                            if (configurationProperties.appConfig().isPresent()) {
                                onError("config.txt had more than one line starting with 'app'. All but the last will"
                                        + " "
                                        + "be ignored.");
                            }
                            final String[] appParams = Arrays.copyOfRange(lineParameters, 2, lineParameters.length);
                            final JarAppConfig appConfig = new JarAppConfig(lineParameters[1], appParams);
                            configurationProperties.setAppConfig(appConfig);
                            break;
                        case ADDRESS_PROPERTY_NAME:
                            if (lineParameters.length >= 8) {
                                final AddressConfig addressConfig = new AddressConfig(
                                        parsOriginalCase[1],
                                        parsOriginalCase[2],
                                        Long.parseLong(pars[3]),
                                        InetAddress.getByName(pars[4]),
                                        Integer.parseInt(pars[5]),
                                        InetAddress.getByName(pars[6]),
                                        Integer.parseInt(pars[7]),
                                        parsOriginalCase[8]);
                                configurationProperties.addAddressConfig(addressConfig);
                            } else {
                                onError("address needs a minimum of 7 parameters");
                            }
                            break;
                        default:
                            logger.error(
                                    EXCEPTION.getMarker(),
                                    "'{}' in config.txt isn't a recognized first parameter for a line",
                                    pars[0]);
                            break;
                    }
                }
            }
            return configurationProperties;
        } catch (final FileNotFoundException ex) {
            // this should never happen
            logger.error(
                    EXCEPTION.getMarker(),
                    "Config.txt file was not found but File#exists() claimed the file does exist",
                    ex);
            throw new IllegalStateException(
                    "Config.txt file was not found but File#exists() claimed the file does exist", ex);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String readNextLine(final Scanner scanner) {
        final String line = scanner.nextLine();
        final int pos = line.indexOf("#");
        if (pos > -1) {
            return line.substring(0, pos).trim();
        }
        return line.trim();
    }

    private static void handleParam(final String propertyName, final int paramLength, final Runnable action) {
        if (paramLength >= 2) {
            action.run();
        } else {
            onError(propertyName + " needs a parameter");
        }
    }

    private static void onError(String message) {
        CommonUtils.tellUserConsolePopup("Error", message);
    }
}
