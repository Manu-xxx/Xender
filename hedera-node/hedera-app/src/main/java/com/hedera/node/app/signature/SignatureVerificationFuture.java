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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * A {@link Future} that waits on a {@link Map} of {@link TransactionSignature}s to complete signature checks, and
 * yields a {@link SignatureVerification}.
 */
public interface SignatureVerificationFuture extends Future<SignatureVerification> {
    /**
     * Gets the hollow account, if any, that will be present on the resulting {@link SignatureVerification}.
     * @return The hollow account, if any.
     */
    @Nullable
    Account hollowAccount();

    /**
     * Gets the key that will be present on the resulting {@link SignatureVerification}.
     * @return The key
     */
    @NonNull
    Key key();
}
