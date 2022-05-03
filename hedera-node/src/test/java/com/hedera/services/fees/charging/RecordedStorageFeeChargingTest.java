package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.ContractStoragePriceTiers;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordedStorageFeeChargingTest {
	private static final long canonicalLifetime = 2592000L;
	private static final ContractStoragePriceTiers canonicalTiers = ContractStoragePriceTiers.from(
			"10@50M,50@100M,100@150M,200@200M,500@250M,700@300M,1000@350M,2000@400M,5000@450M,10000@500M");

	@Mock
	private HbarCentExchange exchange;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private RecordedStorageFeeCharging subject;

	@BeforeEach
	void setUp() {
		subject = new RecordedStorageFeeCharging(exchange, recordsHistorian, txnCtx, dynamicProperties);
		given(dynamicProperties.storageSlotLifetime()).willReturn(canonicalLifetime);
		given(dynamicProperties.storagePriceTiers()).willReturn(canonicalTiers);
		given(txnCtx.consensusTime()).willReturn(now);
		given(exchange.activeRate(now)).willReturn(someRate);
		given(dynamicProperties.fundingAccount()).willReturn(funding);
	}

	@Test
	void chargesBytecode() {
		final var expectedACharge = canonicalTiers.codePrice(
				someRate, canonicalLifetime, numKvPairsUsed, oneKbCode, aExpiry);
		givenChargeableContract(aContract, expectedACharge * 2, aExpiry, null);
		given(accountsLedger.get(funding, BALANCE)).willReturn(0L);

		final var newCodes = Map.of(aContract, oneKbCode);
		subject.chargeStorageFeesInternal(numKvPairsUsed, newCodes, Collections.emptyMap(), accountsLedger);

		verify(accountsLedger).set(aContract, BALANCE, expectedACharge);
		verify(accountsLedger).set(funding, BALANCE, expectedACharge);
	}

	@Test
	void chargesOnlyPositiveDeltasWithAutoRenewAccountPriority() {
		final Map<AccountID, Integer> deltas = new LinkedHashMap<>();
		deltas.put(aContract, +2);
		deltas.put(bContract, -1);
		deltas.put(cContract, +4);
		final var expectedACharge = canonicalTiers.slotPrice(
				someRate, canonicalLifetime, numKvPairsUsed, 2, aExpiry);
		final var expectedCCharge = canonicalTiers.slotPrice(
				someRate, canonicalLifetime, numKvPairsUsed, 4, cExpiry);
		given(accountsLedger.get(funding, BALANCE))
				.willReturn(0L)
				.willReturn(expectedACharge);

		givenChargeableContract(aContract, -1, aExpiry, anAutoRenew);
		givenAutoRenew(anAutoRenew, expectedACharge + 1);
		givenChargeableContract(cContract, expectedCCharge + 2, cExpiry, null);

		subject.chargeStorageFeesInternal(numKvPairsUsed, Collections.emptyMap(), deltas, accountsLedger);

		verify(accountsLedger).set(anAutoRenew, BALANCE, 1L);
		verify(accountsLedger, never()).set(eq(aContract), eq(BALANCE), anyLong());
		verify(accountsLedger).set(cContract, BALANCE, 2L);
		verify(accountsLedger).set(funding, BALANCE, expectedACharge);
		verify(accountsLedger).set(funding, BALANCE, expectedACharge + expectedCCharge);
	}

	@Test
	void fallsBackToContractIfAutoRenewCannotCover() {
		final Map<AccountID, Integer> deltas = Map.of(aContract, +181);
		final var expectedACharge = canonicalTiers.slotPrice(
				someRate, canonicalLifetime, numKvPairsUsed, 181, aExpiry);
		final var autoRenewBalance = expectedACharge / 2;
		final var contractBalance = expectedACharge / 2 + 1;
		given(accountsLedger.get(funding, BALANCE))
				.willReturn(0L)
				.willReturn(autoRenewBalance);

		givenChargeableContract(aContract, contractBalance, aExpiry, anAutoRenew);
		givenAutoRenew(anAutoRenew, autoRenewBalance);

		subject.chargeStorageFeesInternal(numKvPairsUsed, Collections.emptyMap(), deltas, accountsLedger);

		verify(accountsLedger).set(anAutoRenew, BALANCE, 0L);
		verify(accountsLedger).set(aContract, BALANCE, 1L);
		verify(accountsLedger).set(funding, BALANCE, autoRenewBalance);
		verify(accountsLedger).set(funding, BALANCE, expectedACharge);
	}

	@Test
	void failsIfFeesCannotBePaid() {
		final Map<AccountID, Integer> deltas = Map.of(aContract, +181);
		final var expectedACharge = canonicalTiers.slotPrice(
				someRate, canonicalLifetime, numKvPairsUsed, 181, aExpiry);
		final var autoRenewBalance = expectedACharge / 2;
		final var contractBalance = expectedACharge / 2 - 1;
		given(accountsLedger.get(funding, BALANCE))
				.willReturn(0L)
				.willReturn(autoRenewBalance);

		givenChargeableContract(aContract, contractBalance, aExpiry, anAutoRenew);
		givenAutoRenew(anAutoRenew, autoRenewBalance);

		assertFailsWith(() ->
				subject.chargeStorageFeesInternal(numKvPairsUsed, Collections.emptyMap(), deltas, accountsLedger),
				INSUFFICIENT_ACCOUNT_BALANCE);
	}

	private void givenAutoRenew(final AccountID id, final long amount) {
		given(accountsLedger.get(id, BALANCE)).willReturn(amount);
	}

	private void givenChargeableContract(
			final AccountID id,
			final long amount,
			final long expiry,
			@Nullable AccountID autoRenewId
	) {
		if (amount > -1) {
			given(accountsLedger.get(id, BALANCE)).willReturn(amount);
		}
		given(accountsLedger.get(id, EXPIRY)).willReturn(now.getEpochSecond() + expiry);
		if (autoRenewId != null) {
			given(accountsLedger.get(id, AUTO_RENEW_ACCOUNT_ID))
					.willReturn(EntityId.fromGrpcAccountId(autoRenewId));
		} else {
			given(accountsLedger.get(id, AUTO_RENEW_ACCOUNT_ID)).willReturn(EntityId.MISSING_ENTITY_ID);
		}
	}

	private static final AccountID aContract = IdUtils.asAccount("0.0.1234");
	private static final AccountID anAutoRenew = IdUtils.asAccount("0.0.2345");
	private static final AccountID bContract = IdUtils.asAccount("0.0.3456");
	private static final AccountID cContract = IdUtils.asAccount("0.0.4567");
	private static final AccountID funding = IdUtils.asAccount("0.0.98");
	private static final Bytes oneKbCode = Bytes.wrap(new byte[1024]);
	private static final Bytes eightKbCode = Bytes.wrap(new byte[8 * 1024]);
	private static final Instant now = Instant.ofEpochSecond(1_234_567, 890);
	private static final long aExpiry = canonicalLifetime;
	private static final long bExpiry = 2 * canonicalLifetime;
	private static final long cExpiry = 3 * canonicalLifetime / 2;
	private static final long numKvPairsUsed = 100_000_000;

	private static final ExchangeRate someRate = ExchangeRate.newBuilder()
			.setHbarEquiv(12)
			.setCentEquiv(123)
			.build();
}
