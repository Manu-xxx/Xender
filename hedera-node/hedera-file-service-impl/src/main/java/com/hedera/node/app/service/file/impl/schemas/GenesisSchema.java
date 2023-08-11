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

package com.hedera.node.app.service.file.impl.schemas;

import static com.hedera.hapi.node.base.HederaFunctionality.fromString;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static com.hedera.node.app.service.file.impl.FileServiceImpl.UPGRADE_DATA_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.Setting;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitive.ProtoBytes;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.config.Utils;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The initial schema definition for the file service.
 */
public class GenesisSchema extends Schema {
    private static final Logger logger = LogManager.getLogger(GenesisSchema.class);
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;
    /**
     * A hint to the database system of the maximum number of files we will store. This MUST NOT BE CHANGED. If it is
     * changed, then the database has to be rebuilt.
     */
    private static final int MAX_FILES_HINT = 50_000_000;

    /** Create a new instance */
    public GenesisSchema() {
        super(GENESIS_VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(BLOBS_KEY, FileID.PROTOBUF, File.PROTOBUF, MAX_FILES_HINT),
                StateDefinition.queue(UPGRADE_DATA_KEY, ProtoBytes.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        logger.debug("Migrating genesis state");
        final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
        final var filesConfig = ctx.configuration().getConfigData(FilesConfig.class);
        final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
        final WritableKVState<FileID, File> files = ctx.newStates().get(BLOBS_KEY);
        createGenesisAddressBook(bootstrapConfig, filesConfig, files);
        createGenesisNodeDetails(bootstrapConfig, filesConfig, files);
        createGenesisFeeSchedule(bootstrapConfig, hederaConfig, filesConfig, files);
        createGenesisExchangeRate(bootstrapConfig, filesConfig, files);
        createGenesisNetworkProperties(bootstrapConfig, filesConfig, files, ctx.configuration());
        createGenesisHapiPermissions(bootstrapConfig, filesConfig, files);
        createGenesisThrottleDefinitions(bootstrapConfig, filesConfig, files);
        createGenesisSoftwareUpdateZip(bootstrapConfig, filesConfig, files);
    }

    // ================================================================================================================
    // Creates and loads the Address Book into state

    private void createGenesisAddressBook(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis address book file");
        // TBD Implement this method
    }

    // ================================================================================================================
    // Creates and loads the Node Details into state

    private void createGenesisNodeDetails(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis node details file");
        // TBD Implement this method
    }

    // ================================================================================================================
    // Creates and loads the initial Fee Schedule into state

    private void createGenesisFeeSchedule(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis fee schedule file");
        final var resourceName = bootstrapConfig.feeSchedulesJsonResource();
        try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            final var feeScheduleJsonBytes = requireNonNull(in).readAllBytes();
            final var feeSchedule = parseFeeSchedules(feeScheduleJsonBytes);
            final var fileNum = filesConfig.feeSchedules();
            final var fileId = FileID.newBuilder()
                    .fileNum(fileNum)
                    .shardNum(hederaConfig.shard())
                    .realmNum(hederaConfig.realm())
                    .build(); // default to shard=0, realm=0
            final var masterKey =
                    Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
            files.put(
                    fileId,
                    File.newBuilder()
                            .contents(CurrentAndNextFeeSchedule.PROTOBUF.toBytes(feeSchedule))
                            .fileId(fileId)
                            .keys(KeyList.newBuilder().keys(masterKey))
                            .expirationTime(bootstrapConfig.systemEntityExpiry())
                            .build());
        } catch (IOException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Fee schedule (" + resourceName + ") " + "could not be found in the class path", e);
        }
    }

    private static CurrentAndNextFeeSchedule parseFeeSchedules(@NonNull final byte[] feeScheduleJsonBytes) {
        try {
            final var json = new ObjectMapper();
            final var rootNode = json.readTree(feeScheduleJsonBytes);
            final var builder = CurrentAndNextFeeSchedule.newBuilder();
            final var schedules = rootNode.elements();
            while (schedules.hasNext()) {
                final var scheduleContainerNode = schedules.next();
                if (scheduleContainerNode.has("currentFeeSchedule")) {
                    final var currentFeeSchedule = parseFeeSchedule(scheduleContainerNode.get("currentFeeSchedule"));
                    builder.currentFeeSchedule(currentFeeSchedule);
                } else if (scheduleContainerNode.has("nextFeeSchedule")) {
                    final var nextFeeSchedule = parseFeeSchedule(scheduleContainerNode.get("nextFeeSchedule"));
                    builder.nextFeeSchedule(nextFeeSchedule);
                } else {
                    logger.warn("Unexpected node encountered while parsing fee schedule: {}", scheduleContainerNode);
                }
            }

            return builder.build();
        } catch (final Exception e) {
            throw new IllegalArgumentException("Unable to parse fee schedule file", e);
        }
    }

    private static FeeSchedule parseFeeSchedule(@NonNull final JsonNode scheduleNode) {
        final var builder = FeeSchedule.newBuilder();
        final var transactionFeeSchedules = new ArrayList<TransactionFeeSchedule>();
        final var transactionFeeScheduleIterator = scheduleNode.elements();
        while (transactionFeeScheduleIterator.hasNext()) {
            final var childNode = transactionFeeScheduleIterator.next();
            if (childNode.has("transactionFeeSchedule")) {
                final var transactionFeeScheduleNode = childNode.get("transactionFeeSchedule");
                final var feesContainer = transactionFeeScheduleNode.get("fees");
                final var feeDataList = new ArrayList<FeeData>();
                feesContainer.elements().forEachRemaining(feeNode -> feeDataList.add(parseFeeData(feeNode)));
                transactionFeeSchedules.add(TransactionFeeSchedule.newBuilder()
                        .hederaFunctionality(fromString(transactionFeeScheduleNode
                                .get("hederaFunctionality")
                                .asText()))
                        .fees(feeDataList)
                        .build());
            } else if (childNode.has("expiryTime")) {
                final var expiryTime = childNode.get("expiryTime").asLong();
                builder.expiryTime(TimestampSeconds.newBuilder().seconds(expiryTime));
            } else {
                logger.warn("Unexpected node encountered while parsing fee schedule: {}", childNode);
            }
        }

        return builder.transactionFeeSchedule(transactionFeeSchedules).build();
    }

    private static FeeData parseFeeData(@NonNull final JsonNode feeNode) {
        return FeeData.newBuilder()
                .subType(SubType.fromString(feeNode.get("subType").asText()))
                .nodedata(parseFeeComponents(feeNode.get("nodedata")))
                .networkdata(parseFeeComponents(feeNode.get("networkdata")))
                .servicedata(parseFeeComponents(feeNode.get("servicedata")))
                .build();
    }

    private static FeeComponents parseFeeComponents(@NonNull final JsonNode componentNode) {
        return FeeComponents.newBuilder()
                .constant(componentNode.get("constant").asLong())
                .bpt(componentNode.get("bpt").asLong())
                .vpt(componentNode.get("vpt").asLong())
                .rbh(componentNode.get("rbh").asLong())
                .sbh(componentNode.get("sbh").asLong())
                .gas(componentNode.get("gas").asLong())
                .bpr(componentNode.get("bpr").asLong())
                .sbpr(componentNode.get("sbpr").asLong())
                .min(componentNode.get("min").asLong())
                .max(componentNode.get("max").asLong())
                .build();
    }

    // ================================================================================================================
    // Creates and loads the initial Exchange Rate into state

    private void createGenesisExchangeRate(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis exchange rate file");
        // See HfsSystemFilesManager#defaultRates. This does the same thing.
        final var exchangeRateSet = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesCurrentCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesCurrentHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesCurrentExpiry()))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesNextCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesNextHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesNextExpiry()))
                        .build())
                .build();

        final var fileNum = filesConfig.exchangeRates();
        final var fileId = FileID.newBuilder().fileNum(fileNum).build(); // default to shard=0, realm=0
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ExchangeRateSet.PROTOBUF.toBytes(exchangeRateSet))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationTime(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the network properties into state

    private void createGenesisNetworkProperties(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files,
            @NonNull final Configuration configuration) {
        logger.debug("Creating genesis network properties file");

        // Get the set of network properties from configuration, and generate the file content to store in state.
        List<Setting> settings = new ArrayList<>();
        Utils.networkProperties(configuration)
                .forEach((propertyName, propertyValue) -> settings.add(Setting.newBuilder()
                        .name(propertyName)
                        .value(propertyValue.toString())
                        .build()));

        final var servicesConfigList =
                ServicesConfigurationList.newBuilder().nameValue(settings).build();
        final var fileNum = filesConfig.networkProperties();
        // default to shard=0, realm=0. Should get from config
        final var fileId = FileID.newBuilder().fileNum(fileNum).build();
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ServicesConfigurationList.PROTOBUF.toBytes(servicesConfigList))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationTime(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the HAPI Permissions into state

    private void createGenesisHapiPermissions(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis HAPI permissions file");

        // Get the path to the HAPI permissions file
        final var pathToApiPermissions = Path.of(bootstrapConfig.feeSchedulesJsonResource());

        // If the file exists, load from there
        String apiPermissionsContent = null;
        if (Files.exists(pathToApiPermissions)) {
            try {
                apiPermissionsContent = Files.readString(pathToApiPermissions);
                logger.info("API Permissions loaded from {}", pathToApiPermissions);
            } catch (IOException e) {
                logger.error(
                        "API Permissions could not be loaded from {}, looking for fallback on classpath",
                        pathToApiPermissions);
            }
        }
        // Otherwise, load from the classpath. If that cannot be done, we have a totally broken build.
        if (apiPermissionsContent == null) {
            final var resourceName = "api-permission.properties";
            try (final var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
                apiPermissionsContent = new String(requireNonNull(in).readAllBytes(), UTF_8);
                logger.info("API Permissions loaded from classpath resource {}", resourceName);
            } catch (IOException | NullPointerException e) {
                logger.fatal("API Permissions could not be loaded from classpath");
                throw new IllegalArgumentException("API Permissions could not be loaded from classpath", e);
            }
        }

        // Parse the HAPI permissions file into a ServicesConfigurationList protobuf object
        final var settings = new ArrayList<Setting>();
        try (final var in = new StringReader(apiPermissionsContent)) {
            final var props = new Properties();
            props.load(in);
            props.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> settings.add(Setting.newBuilder()
                            .name(String.valueOf(entry.getKey()))
                            .value(String.valueOf(entry.getValue()))
                            .build()));
        } catch (final IOException e) {
            logger.fatal("API Permissions could not be parsed");
            throw new IllegalArgumentException("API Permissions could not be parsed", e);
        }

        // Store the configuration in state
        final var fileNum = filesConfig.hapiPermissions();
        final var fileId = FileID.newBuilder().fileNum(fileNum).build(); // default to shard=0, realm=0
        final var masterKey =
                Key.newBuilder().ed25519(bootstrapConfig.genesisPublicKey()).build();
        files.put(
                fileId,
                File.newBuilder()
                        .contents(ServicesConfigurationList.PROTOBUF.toBytes(ServicesConfigurationList.newBuilder()
                                .nameValue(settings)
                                .build()))
                        .fileId(fileId)
                        .keys(KeyList.newBuilder().keys(masterKey))
                        .expirationTime(bootstrapConfig.systemEntityExpiry())
                        .build());
    }

    // ================================================================================================================
    // Creates and loads the Throttle definitions into state

    private void createGenesisThrottleDefinitions(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis throttle definitions file");
        // TBD Implement this method
    }

    // ================================================================================================================
    // Creates and loads the software update file into state (may be empty? NOT SURE)

    private void createGenesisSoftwareUpdateZip(
            @NonNull final BootstrapConfig bootstrapConfig,
            @NonNull final FilesConfig filesConfig,
            @NonNull final WritableKVState<FileID, File> files) {
        logger.debug("Creating genesis software update zip file");
        // TBD Implement this method
    }
}
