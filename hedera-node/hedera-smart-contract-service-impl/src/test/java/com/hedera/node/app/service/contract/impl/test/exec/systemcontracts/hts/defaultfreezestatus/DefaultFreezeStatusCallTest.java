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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.defaultfreezestatus;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultfreezestatus.DefaultFreezeStatusCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultfreezestatus.DefaultFreezeStatusTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFreezeStatusCallTest extends HtsCallTestBase {
    @Test
    void returnsDefaultFreezeStatusForPresentToken() {
        var subject = new DefaultFreezeStatusCall(mockEnhancement(), FUNGIBLE_TOKEN);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(DefaultFreezeStatusTranslator.DEFAULT_FREEZE_STATUS
                        .getOutputs()
                        .encodeElements(SUCCESS.protoOrdinal(), false)
                        .array()),
                result.getOutput());
    }

    @Test
    void returnsDefaultFreezeStatusForMissingToken() {
        var subject = new DefaultFreezeStatusCall(mockEnhancement(), null);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(DefaultFreezeStatusTranslator.DEFAULT_FREEZE_STATUS
                        .getOutputs()
                        .encodeElements(INVALID_TOKEN_ID.protoOrdinal(), false)
                        .array()),
                result.getOutput());
    }
}
