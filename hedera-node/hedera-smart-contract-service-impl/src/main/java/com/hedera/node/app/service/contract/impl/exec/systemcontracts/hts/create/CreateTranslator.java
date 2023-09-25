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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.service.contract.impl.utils.ParsingConstantsUtils.ARRAY_BRACKETS;
import static com.hedera.node.app.service.contract.impl.utils.ParsingConstantsUtils.EXPIRY;
import static com.hedera.node.app.service.contract.impl.utils.ParsingConstantsUtils.TOKEN_KEY;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

public class CreateTranslator extends AbstractHtsCallTranslator {

    public static final String HEDERA_TOKEN_STRUCT =
            "(string,string,address,string,bool,uint32,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";

    public static final Function CREATE_FUNGIBLE_TOKEN =
            new Function("createFungibleToken(" + HEDERA_TOKEN_STRUCT + ",int64,int32)", ReturnTypes.INT);

    @Inject
    public CreateTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        if (Arrays.equals(selector, CreateTranslator.CREATE_FUNGIBLE_TOKEN.selector())) {
            call = CreateTranslator.CREATE_FUNGIBLE_TOKEN.decodeCall(
                    attempt.input().toArrayUnsafe());
        } else {

        }
        throw new AssertionError("Not implemented");
    }
}
