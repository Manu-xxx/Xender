package com.hedera.services.bdd.suites.jrs;

import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.freeze.UpdateFileForUpgrade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;

public class DeleteNodeForUpgrade extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DeleteNodeForUpgrade.class);

    public static void main(String... args) {
        new DeleteNodeForUpgrade().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(doDelete());
    }

    final Stream<DynamicTest> doDelete() {
        return defaultHapiSpec("DeleteNodeForUpgrade")
                .given(initializeSettings())
                .when(nodeDelete("0").signedBy(GENESIS))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
