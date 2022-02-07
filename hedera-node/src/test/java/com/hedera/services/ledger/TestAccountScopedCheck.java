package com.hedera.services.ledger;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.ledger.properties.TestAccountProperty;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.FUNGIBLE_ALLOWANCES;
import static com.hedera.services.ledger.properties.TestAccountProperty.HBAR_ALLOWANCES;
import static com.hedera.services.ledger.properties.TestAccountProperty.LONG;
import static com.hedera.services.ledger.properties.TestAccountProperty.NFT_ALLOWANCES;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;

class TestAccountScopedCheck implements LedgerCheck<TestAccount, TestAccountProperty> {
	@Override
	public ResponseCodeEnum checkUsing(final TestAccount account, final Map<TestAccountProperty, Object> changeSet) {
		Function<TestAccountProperty, Object> getter = prop -> {
			if (changeSet != null && changeSet.containsKey(prop)) {
				return changeSet.get(prop);
			} else {
				return prop.getter().apply(account);
			}
		};
		return checkUsing(getter, changeSet);
	}

	@Override
	public ResponseCodeEnum checkUsing(
			final Function<TestAccountProperty, Object> extantProps,
			final Map<TestAccountProperty, Object> changeSet
	) {
		if ((boolean) extantProps.apply(FLAG)) {
			return ACCOUNT_IS_TREASURY;
		}
		if ((long) extantProps.apply(LONG) != 123L) {
			return ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
		}
		if (!extantProps.apply(OBJ).equals("DEFAULT")) {
			return ACCOUNT_STILL_OWNS_NFTS;
		}
		if (extantProps.apply(HBAR_ALLOWANCES) == TestAccount.Allowance.MISSING) {
			return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
		}
		if (extantProps.apply(HBAR_ALLOWANCES) == TestAccount.Allowance.INSUFFICIENT) {
			return AMOUNT_EXCEEDS_ALLOWANCE;
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum validateNftAllowance(final TestAccount account,
			final Function<TestAccountProperty, Object> extantProps,
			final Map<TestAccountProperty, Object> changeSet) {
		if (account == null) {
			if (extantProps.apply(NFT_ALLOWANCES) != TestAccount.Allowance.OK) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
		} else {
			if (account.getValidNftAllowances() != TestAccount.Allowance.OK) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum validateFungibleTokenAllowance(final TestAccount account,
			final Function<TestAccountProperty, Object> extantProps, final Map<TestAccountProperty, Object> changeSet) {
		if (account == null) {
			if (extantProps.apply(FUNGIBLE_ALLOWANCES) == TestAccount.Allowance.MISSING) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
			if (extantProps.apply(FUNGIBLE_ALLOWANCES) == TestAccount.Allowance.INSUFFICIENT) {
				return AMOUNT_EXCEEDS_ALLOWANCE;
			}
		} else {
			if (account.getValidFungibleAllowances() == TestAccount.Allowance.MISSING) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
			if (account.getValidFungibleAllowances() == TestAccount.Allowance.INSUFFICIENT) {
				return AMOUNT_EXCEEDS_ALLOWANCE;
			}
		}
		return OK;
	}

}
