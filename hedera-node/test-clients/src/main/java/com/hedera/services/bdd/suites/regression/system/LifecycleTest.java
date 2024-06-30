/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.junit.hedera.MarkerFile.EXEC_IMMEDIATE_MF;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.buildUpgradeZipFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restartNetworkWithConfigVersion;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.shutdownNetworkWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForActiveNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForFrozenNetwork;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitForMf;
import static com.hedera.services.bdd.spec.utilops.upgrade.BuildUpgradeZipOp.FAKE_UPGRADE_ZIP_LOC;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_FILE_ID;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.FAKE_ASSETS_LOC;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileAppendsPerBurst;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileHashAt;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Implementation support for tests that exercise the Hedera network lifecycle, including freezes,
 * restarts, software upgrades, and reconnects.
 */
public interface LifecycleTest {
    Duration FREEZE_TIMEOUT = Duration.ofSeconds(90);
    Duration RESTART_TIMEOUT = Duration.ofSeconds(180);
    Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(60);
    Duration EXEC_IMMEDIATE_MF_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Returns an operation that asserts that the current version of the network has the given
     * semantic version modified by the given config version.
     *
     * @param versionSupplier the supplier of the expected version
     * @param configVersion the expected configuration version
     * @return the operation
     */
    default HapiSpecOperation assertVersion(
            @NonNull final Supplier<SemanticVersion> versionSupplier, final int configVersion) {
        return sourcing(() ->
                getVersionInfo().hasProtoServicesVersion(fromBaseAndConfig(versionSupplier.get(), configVersion)));
    }

    /**
     * Returns an operation that builds a fake upgrade ZIP, uploads it to file {@code 0.0.150),
     * issues a {@code PREPARE_UPGRADE}, and awaits writing of the <i>execute_immediate.mf</i>.
     * @return the operation
     */
    default HapiSpecOperation prepareFakeUpgrade() {
        return blockingOrder(
                buildUpgradeZipFrom(FAKE_ASSETS_LOC),
                // Upload it to file 0.0.150; need sourcing() here because the operation reads contents eagerly
                sourcing(() -> updateSpecialFile(
                        GENESIS,
                        DEFAULT_UPGRADE_FILE_ID,
                        FAKE_UPGRADE_ZIP_LOC,
                        TxnUtils.BYTES_4K,
                        upgradeFileAppendsPerBurst())),
                // Issue PREPARE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> prepareUpgrade()
                        .withUpdateFile(DEFAULT_UPGRADE_FILE_ID)
                        .havingHash(upgradeFileHashAt(FAKE_UPGRADE_ZIP_LOC))),
                // Wait for the immediate execution marker file (written only after 0.0.150 is unzipped)
                waitForMf(EXEC_IMMEDIATE_MF, LifecycleTest.EXEC_IMMEDIATE_MF_TIMEOUT));
    }

    /**
     * Returns an operation that upgrades the network to the given configuration version using a fake upgrade ZIP.
     * @param version the configuration version to upgrade to
     * @return the operation
     */
    default HapiSpecOperation upgradeToConfigVersion(final int version) {
        return upgradeToConfigVersion(version, noOp());
    }

    /**
     * Returns an operation that upgrades the network to the given configuration version using a fake upgrade ZIP,
     * running the given operation before the network is restarted.
     *
     * @param version the configuration version to upgrade to
     * @param preRestartOp an operation to run before the network is restarted
     * @return the operation
     */
    default HapiSpecOperation upgradeToConfigVersion(final int version, @NonNull final SpecOperation preRestartOp) {
        requireNonNull(preRestartOp);
        return blockingOrder(
                sourcing(() -> freezeUpgrade()
                        .startingIn(2)
                        .seconds()
                        .withUpdateFile(DEFAULT_UPGRADE_FILE_ID)
                        .havingHash(upgradeFileHashAt(FAKE_UPGRADE_ZIP_LOC))),
                confirmFreezeAndShutdown(),
                preRestartOp,
                restartNetworkWithConfigVersion(version),
                waitForActiveNetwork(RESTART_TIMEOUT));
    }

    /**
     * Returns an operation that confirms the network has been frozen and shut down.
     * @return the operation
     */
    default HapiSpecOperation confirmFreezeAndShutdown() {
        return blockingOrder(
                waitForFrozenNetwork(FREEZE_TIMEOUT),
                // Shut down all nodes, since the platform doesn't automatically go back to ACTIVE status
                shutdownNetworkWithin(SHUTDOWN_TIMEOUT));
    }

    /**
     * Returns a {@link SemanticVersion} that combines the given version with the given configuration version.
     *
     * @param version the base version
     * @param configVersion the configuration version
     * @return the combined version
     */
    static SemanticVersion fromBaseAndConfig(@NonNull SemanticVersion version, int configVersion) {
        return (configVersion == 0)
                ? version
                : version.toBuilder().setBuild("" + configVersion).build();
    }
}
