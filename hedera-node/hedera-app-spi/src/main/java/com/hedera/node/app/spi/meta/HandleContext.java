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

package com.hedera.node.app.spi.meta;

import com.hedera.node.app.spi.CallContext;
import com.hedera.node.app.spi.accounts.AccountLookup;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.EntityCreationLimits;
import com.hedera.node.app.spi.validation.EntityExpiryValidator;
import java.time.Instant;
import java.util.function.LongSupplier;

public interface HandleContext {
    Instant consensusNow();

    LongSupplier newEntityNumSupplier();

    AttributeValidator attributeValidator();

    EntityExpiryValidator expiryValidator();

    EntityCreationLimits entityCreationLimits();

    AccountLookup accountLookup();
}
