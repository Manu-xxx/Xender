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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.burn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn.BurnTranslator.BURN_TOKEN_V1;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn.FungibleBurnCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class FungibleBurnCallTest extends HtsCallTestBase {

    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private TokenBurnRecordBuilder recordBuilder;

    @Mock
    private Token token;

    private FungibleBurnCall subject;

    @Test
    void revertsOnMissingToken() {
        subject = new FungibleBurnCall(
                mockEnhancement(), null, 1234, verificationStrategy, FRAME_SENDER_ADDRESS, addressIdConverter);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(Bytes.wrap(INVALID_TOKEN_ID.protoName().getBytes()), result.getOutput());
    }

    @Test
    void burnTokenHappyPath() {
        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(TokenBurnRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(nativeOperations.getToken(FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        given(nativeOperations.getToken(9876L).totalSupply()).willReturn(100L);

        subject = subjectForBurn(10L);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(BURN_TOKEN_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(22), BigInteger.valueOf(token.totalSupply()))),
                result.getOutput());
    }

    // @TODO add test for V2

    private FungibleBurnCall subjectForBurn(final long amount) {
        return new FungibleBurnCall(
                mockEnhancement(),
                FUNGIBLE_TOKEN_ID,
                amount,
                verificationStrategy,
                FRAME_SENDER_ADDRESS,
                addressIdConverter);
    }
}
