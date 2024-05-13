/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.expectedEntitiesExist;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class AddWellKnownEntities extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddWellKnownEntities.class);

    public static void main(String... args) {
        var hero = new AddWellKnownEntities();

        hero.runSuiteSync();
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(instantiateEntities());
    }

    final DynamicTest instantiateEntities() {
        return HapiSpec.customHapiSpec("AddWellKnownEntities")
                .withProperties(Map.of(
                        "fees.useFixedOffer", "true",
                        "fees.fixedOffer", "" + ONE_HUNDRED_HBARS,
                        "persistentEntities.dir.path", "src/main/resource/jrs-creations"))
                .given(expectedEntitiesExist())
                .when()
                .then(sleepFor(10_000L), freezeOnly().startingIn(60).seconds().payingWith(GENESIS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
