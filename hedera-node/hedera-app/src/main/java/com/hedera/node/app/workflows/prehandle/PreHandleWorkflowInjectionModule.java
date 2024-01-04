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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.signature.MonoSignaturePreparer;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.spi.workflows.PreHandleDispatcher;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

@Module
public interface PreHandleWorkflowInjectionModule {
    @Provides
    static Function<SignatureMap, PubKeyToSigBytes> provideKeyToSigFactory() {
        return signatureMap -> new PojoSigMapPubKeyToSigBytes(PbjConverter.fromPbj(signatureMap));
    }

    @Binds
    PreHandleWorkflow bindPreHandleWorkflow(PreHandleWorkflowImpl preHandleWorkflow);

    @Binds
    SignaturePreparer bindSignaturePreparer(MonoSignaturePreparer signaturePreparer);

    @Binds
    SignatureVerifier bindSignatureVerifier(SignatureVerifierImpl signatureVerifier);

    @Binds
    SignatureExpander bindSignatureExpander(SignatureExpanderImpl signatureExpander);

    /**
     * This binding is only needed to have a PreHandleDispatcher implementation that can be provided by dagger.
     */
    @Deprecated
    @Binds
    PreHandleDispatcher bindPreHandleDispatcher(DummyPreHandleDispatcher preHandleDispatcher);

    @Provides
    static ExecutorService provideExecutorService() {
        return ForkJoinPool.commonPool();
    }
}
