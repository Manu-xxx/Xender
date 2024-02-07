/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.sample.internal;

import com.swirlds.base.sample.domain.Balance;
import com.swirlds.base.sample.domain.Wallet;
import com.swirlds.base.sample.persistence.BalanceDao;
import com.swirlds.base.sample.persistence.Version;
import com.swirlds.base.sample.persistence.WalletDao;
import java.math.BigDecimal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InternalTestData {

    private static final Logger log = LogManager.getLogger(InternalTestData.class);

    public static void create() {
        WalletDao.getInstance().save(new Wallet("0"));
        BalanceDao.getInstance().save(new Balance(new Wallet("0"), BigDecimal.valueOf(Long.MAX_VALUE), new Version(0)));
        log.debug("Created internal test data");
    }
}
