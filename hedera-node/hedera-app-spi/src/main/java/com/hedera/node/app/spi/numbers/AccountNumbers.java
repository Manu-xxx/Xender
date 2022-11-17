/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.numbers;

/**
 * Represents different types of special accounts used in the ledger.
 */
public interface AccountNumbers {
    /**
     * Account Number representing treasury
     * @return treasury account number
     */
    long treasury();
    /**
     * Account Number representing freeze admin
     * @return freeze admin account number
     */
    long freezeAdmin();
    /**
     * Account Number representing system admin
     * @return system admin account number
     */
    long systemAdmin();
    /**
     * Account Number representing address book admin
     * @return address book admin account
     */
    long addressBookAdmin();
    /**
     * Account Number representing fee schedule admin
     * @return fee schedule admin account number
     */
    long feeSchedulesAdmin();
    /**
     * Account Number representing exchange rate admin
     * @return exchange rate admin account number
     */
    long exchangeRatesAdmin();
    /**
     * Account Number representing system delete admin
     * @return system delete admin account number
     */
    long systemDeleteAdmin();
    /**
     * Account Number representing system undelete admin
     * @return system undelete admin account number
     */
    long systemUndeleteAdmin();
    /**
     * Account Number representing staking reward account number
     * @return staking reward account number
     */
    long stakingRewardAccount();
    /**
     * account number Number representing node reward account number
     * @return node reward account number
     */
    long nodeRewardAccount();
    /**
     * Checks if the account number provided is superuser
     * @return true if superuser account, false otherwise
     */
    default boolean isSuperuser(long num) {
        return num == treasury() || num == systemAdmin();
    }
}
