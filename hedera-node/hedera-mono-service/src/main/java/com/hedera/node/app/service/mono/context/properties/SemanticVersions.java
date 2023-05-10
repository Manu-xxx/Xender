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

package com.hedera.node.app.service.mono.context.properties;

import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum SemanticVersions {
    SEMANTIC_VERSIONS;

    private static final Logger log = LogManager.getLogger(SemanticVersions.class);

    /* From https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string */
    private static final Pattern SEMVER_SPEC_REGEX = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
                    + "(?:\\."
                    + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)"
                    + "*))?$");

    private static final String HAPI_VERSION_KEY = "hapi.proto.version";
    private static final String HEDERA_VERSION_KEY = "hedera.services.version";
    private static final String HEDERA_CONFIG_VERSION_KEY = "hedera.config.version";
    private static final String VERSION_INFO_RESOURCE = "semantic-version.properties";

    private final AtomicReference<ActiveVersions> knownActive = new AtomicReference<>(null);
    private final AtomicReference<SerializableSemVers> knownSerializable = new AtomicReference<>(null);

    @NonNull
    public ActiveVersions getDeployed() {
        ensureLoaded();
        return knownActive.get();
    }

    @NonNull
    public SerializableSemVers deployedSoftwareVersion() {
        ensureLoaded();
        return knownSerializable.get();
    }

    private void ensureLoaded() {
        if (knownActive.get() == null) {
            final var deployed = fromResource(
                    VERSION_INFO_RESOURCE,
                    HAPI_VERSION_KEY,
                    HEDERA_VERSION_KEY,
                    HEDERA_CONFIG_VERSION_KEY);
            knownActive.set(deployed);
            knownSerializable.set(new SerializableSemVers(deployed.protoSemVer(), deployed.hederaSemVer()));
        }
    }

    @NonNull
    static ActiveVersions fromResource(
            final String propertiesFile,
            final String protoKey,
            final String servicesKey,
            final String configKey) {
        try (final var in = SemanticVersions.class.getClassLoader().getResourceAsStream(propertiesFile)) {
            final var props = new Properties();
            props.load(in);
            log.info("Discovered semantic versions {} from resource '{}'", props, propertiesFile);

            // construct semantic versions from "semantic-version.properties" file.
            final var protoSemVer = asSemVer((String) props.get(protoKey));
            final var hederaSemVer = asSemVer((String) props.get(servicesKey));

            // Loads all the configuration properties from "bootstrap.properties" file.
            final var bootstrapProperties = new BootstrapProperties(false);
            final var configVersion = bootstrapProperties.getIntProperty(configKey);
            log.info("Discovered configuration version {} from resource 'bootstrap.properties'", configVersion);

            // append the build portion of semver with the configurable property "hedera.config.version"
            // This is needed only for internal use to do config-only upgrades.
            final var hederaSemVerWithConfig = addConfigVersionToBuild(String.valueOf(configVersion), hederaSemVer);
            return new ActiveVersions(protoSemVer, hederaSemVerWithConfig);
        } catch (final Exception surprising) {
            log.warn(
                    "Failed to parse resource '{}' (keys '{}' and '{}') and resource 'bootstrap.properties' (key '{}')"
                            + ". Version info will be" + " unavailable!",
                    propertiesFile,
                    protoKey,
                    servicesKey,
                    configKey,
                    surprising);
            final var emptySemver = SemanticVersion.getDefaultInstance();
            return new ActiveVersions(emptySemver, emptySemver);
        }
    }

    /**
     * Appends the build portion of semver from the configurable property {@code hedera.config.version}.
     * If the configured value is empty or 0, then the original semver is returned.
     * This is needed only for internal use to do config-only upgrades.
     *
     * @param configVersion the configured value
     * @param hederaSemVer the original semver
     * @return the semver with the build portion appended
     */
    static SemanticVersion addConfigVersionToBuild(
            @NonNull final String configVersion, @NonNull final SemanticVersion hederaSemVer) {
        if (!configVersion.isEmpty() && !configVersion.equals("0")) {
            return hederaSemVer.toBuilder().setBuild(configVersion).build();
        }
        return hederaSemVer;
    }

    static SemanticVersion asSemVer(final String value) {
        final var matcher = SEMVER_SPEC_REGEX.matcher(value);
        if (matcher.matches()) {
            final var builder = SemanticVersion.newBuilder()
                    .setMajor(Integer.parseInt(matcher.group(1)))
                    .setMinor(Integer.parseInt(matcher.group(2)))
                    .setPatch(Integer.parseInt(matcher.group(3)));
            if (matcher.group(4) != null) {
                builder.setPre(matcher.group(4));
            }
            if (matcher.group(5) != null) {
                builder.setBuild(matcher.group(5));
            }
            return builder.build();
        } else {
            throw new IllegalArgumentException("Argument value='" + value + "' is not a valid semver");
        }
    }
}
