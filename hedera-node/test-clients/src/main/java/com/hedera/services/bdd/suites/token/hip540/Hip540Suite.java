/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token.hip540;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.suites.token.hip540.Hip540TestScenarios.ALL_HIP_540_SCENARIOS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class Hip540Suite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Hip540Suite.class);

    public static void main(String... args) {
        new Hip540Suite().runSuiteSync();
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(allScenariosAsExpected());
    }

    @HapiTest
    public final DynamicTest allScenariosAsExpected() {
        return defaultHapiSpec("allScenariosAsExpected")
                .given()
                .when()
                .then(inParallel(ALL_HIP_540_SCENARIOS.stream()
                        .map(Hip540TestScenario::asOperation)
                        .toArray(HapiSpecOperation[]::new)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
