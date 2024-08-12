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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.isauthorizedraw;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawTranslator.IS_AUTHORIZED_RAW;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.messageHash;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.signature;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHasAttemptWithSelector;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.v051.Version051FeatureFlags;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsAuthorizedRawTranslatorTest {

    @Mock(strictness = Strictness.LENIENT) // might not use `configuration()`
    private HasCallAttempt attempt;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private CustomGasCalculator customGasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private HederaNativeOperations nativeOperations;

    private IsAuthorizedRawTranslator subject;

    @BeforeEach
    void setUp() {
        final var featureFlags = new Version051FeatureFlags();
        subject = new IsAuthorizedRawTranslator(featureFlags, customGasCalculator);
    }

    @Test
    void matchesIsAuthorizedRawWhenEnabled() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHasAttemptWithSelector(
                IS_AUTHORIZED_RAW, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void doesNotMatchIsAuthorizedRawWhenDisabled() {
        given(attempt.configuration()).willReturn(getTestConfiguration(false));
        given(attempt.selector()).willReturn(IS_AUTHORIZED_RAW.selector());
        var matches = subject.matches(attempt);
        assertFalse(matches);
    }

    @Test
    void failsOnInvalidSelector() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHasAttemptWithSelector(
                HBAR_ALLOWANCE_PROXY, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertFalse(subject.matches(attempt));
    }

    @Test
    void callFromIsAuthorizedRawTest() {
        given(attempt.configuration()).willReturn(getTestConfiguration(true));
        final Bytes inputBytes = Bytes.wrapByteBuffer(
                IS_AUTHORIZED_RAW.encodeCall(Tuple.of(APPROVED_HEADLONG_ADDRESS, messageHash, signature)));
        givenCommonForCall(inputBytes);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(IsAuthorizedRawCall.class);
    }

    private void givenCommonForCall(Bytes inputBytes) {
        given(attempt.inputBytes()).willReturn(inputBytes.toArray());
        given(attempt.isSelector(any())).willReturn(true);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
    }

    @NonNull
    Configuration getTestConfiguration(final boolean enableIsAuthorizedRaw) {
        return HederaTestConfigBuilder.create()
                .withValue("contracts.systemContract.accountService.isAuthorizedRawEnabled", enableIsAuthorizedRaw)
                .getOrCreateConfig();
    }
}
