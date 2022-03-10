package com.hedera.services.contracts.sources;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.ActivationTest;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JContractAliasKey;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.crypto.TransactionSignature;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TxnAwareEvmSigsVerifierTest {
	private static final Address PRETEND_RECIPIENT_ADDR = Address.ALTBN128_ADD;
	private static final Address PRETEND_CONTRACT_ADDR = Address.ALTBN128_MUL;
	private static final Address PRETEND_SENDER_ADDR = Address.ALTBN128_PAIRING;
	private static final Id tokenId = new Id(0, 0, 666);
	private static final Id accountId = new Id(0, 0, 1234);
	private static final Address PRETEND_TOKEN_ADDR = tokenId.asEvmAddress();
	private static final Address PRETEND_ACCOUNT_ADDR = accountId.asEvmAddress();
	private final TokenID token = IdUtils.asToken("0.0.666");
	private final AccountID payer = IdUtils.asAccount("0.0.2");
	private final AccountID account = IdUtils.asAccount("0.0.1234");
	private final AccountID sigRequired = IdUtils.asAccount("0.0.555");
	private final AccountID smartContract = IdUtils.asAccount("0.0.666");
	private final AccountID noSigRequired = IdUtils.asAccount("0.0.777");

	private JKey expectedKey;

	@Mock
	private MerkleAccount contract;
	@Mock
	private MerkleAccount sigReqAccount;
	@Mock
	private MerkleAccount noSigReqAccount;
	@Mock
	private BiPredicate<JKey, TransactionSignature> cryptoValidity;
	@Mock
	private ActivationTest activationTest;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private Function<byte[], TransactionSignature> pkToCryptoSigsFn;
	@Mock
	private ContractAliases aliases;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
	@Mock
	private WorldLedgers ledgers;

	private TxnAwareEvmSigsVerifier subject;

	@BeforeEach
	private void setup() throws Exception {
		expectedKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKey();

		subject = new TxnAwareEvmSigsVerifier(activationTest, txnCtx, cryptoValidity);
	}

	@Test
	void throwsIfAskedToVerifyMissingToken() {
		given(ledgers.tokens()).willReturn(tokensLedger);
		given(tokensLedger.getImmutableRef(token)).willReturn(null);

		assertFailsWith(() ->
						subject.hasActiveSupplyKey(true,
								PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR,
								ledgers),
				INVALID_TOKEN_ID);
	}

	@Test
	void throwsIfLedgersAreNullForActiveSupplyKey() {
		assertFailsWith(() ->
						subject.hasActiveSupplyKey(true,
								PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR,
								null),
				FAIL_INVALID);
	}

	@Test
	void throwsIfLedgersAreNullForActiveKey() {
		assertFailsWith(() ->
						subject.hasActiveKey(true,
								PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR,
								null),
				FAIL_INVALID);
	}

	@Test
	void throwsIfAskedToVerifyTokenWithoutSupplyKey() {
		final var merkleToken = mock(MerkleToken.class);

		given(ledgers.tokens()).willReturn(tokensLedger);
		given(tokensLedger.getImmutableRef(token)).willReturn(merkleToken);
		given(merkleToken.hasSupplyKey()).willReturn(false);

		assertFailsWith(() ->
						subject.hasActiveSupplyKey(true,
								PRETEND_TOKEN_ADDR,
								PRETEND_SENDER_ADDR,
								ledgers),
				TOKEN_HAS_NO_SUPPLY_KEY);
	}

	@Test
	void testsSupplyKeyIfPresent() {
		given(txnCtx.accessor()).willReturn(accessor);
		final var merkleToken = mock(MerkleToken.class);
		given(ledgers.tokens()).willReturn(tokensLedger);
		given(tokensLedger.getImmutableRef(token)).willReturn(merkleToken);
		given(merkleToken.hasSupplyKey()).willReturn(true);
		given(merkleToken.getSupplyKey()).willReturn(expectedKey);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		final var verdict = subject.hasActiveSupplyKey(true,
				PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

		assertTrue(verdict);
	}

	@Test
	void supplyKeyFailsWhenTokensLedgerIsNull() {
		given(ledgers.tokens()).willReturn(null);

		assertFailsWith(() ->
						subject.hasActiveSupplyKey(true,
								PRETEND_TOKEN_ADDR,
								PRETEND_SENDER_ADDR,
								ledgers),
				INVALID_TOKEN_ID);
	}

	@Test
	void throwsIfAskedToVerifyMissingAccount() {
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(account)).willReturn(null);

		assertFailsWith(() ->
						subject.hasActiveKey(true,
								PRETEND_ACCOUNT_ADDR,
								PRETEND_SENDER_ADDR,
								ledgers),
				INVALID_ACCOUNT_ID);
	}

	@Test
	void testsAccountKeyIfPresent() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(account)).willReturn(sigReqAccount);
		given(sigReqAccount.getAccountKey()).willReturn(expectedKey);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		final var verdict = subject.hasActiveKey(true,
				PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers);

		assertTrue(verdict);
	}

	@Test
	void activeKeyFailsWhenAccountsLedgerIsNull() {
		given(ledgers.accounts()).willReturn(null);

		assertFailsWith(() ->
						subject.hasActiveKey(true,
								PRETEND_ACCOUNT_ADDR,
								PRETEND_SENDER_ADDR,
								ledgers),
				INVALID_ACCOUNT_ID);
	}

	@Test
	void filtersContracts() {
		given(txnCtx.activePayer()).willReturn(payer);
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(smartContract)).willReturn(contract);
		given(contract.isSmartContract()).willReturn(true);

		final var contractFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(smartContract), PRETEND_SENDER_ADDR, ledgers);

		assertTrue(contractFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void filtersNoSigRequired() {
		given(txnCtx.activePayer()).willReturn(payer);
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(noSigRequired)).willReturn(noSigReqAccount);

		final var noSigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(noSigRequired), PRETEND_SENDER_ADDR, ledgers);

		assertTrue(noSigRequiredFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void filtersNoSigRequiredWhenLedgersAreNull() {
		given(txnCtx.activePayer()).willReturn(payer);

		final var noSigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(noSigRequired), PRETEND_SENDER_ADDR, null);

		assertTrue(noSigRequiredFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void filtersNoSigRequiredWhenLedgersAreNotNullButAccountsLedgerIsNull() {
		given(txnCtx.activePayer()).willReturn(payer);
		given(ledgers.accounts()).willReturn(null);

		final var noSigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(noSigRequired), PRETEND_SENDER_ADDR, ledgers);

		assertTrue(noSigRequiredFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void testsWhenReceiverSigIsRequired() {
		givenAccessorInCtx();
		given(sigReqAccount.isReceiverSigRequired()).willReturn(true);
		given(sigReqAccount.getAccountKey()).willReturn(expectedKey);
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(sigRequired)).willReturn(sigReqAccount);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);

		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		boolean sigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(sigRequired), PRETEND_SENDER_ADDR, ledgers);

		assertTrue(sigRequiredFlag);
	}

	@Test
	void filtersPayerSinceSigIsGuaranteed() {
		given(txnCtx.activePayer()).willReturn(payer);

		boolean payerFlag = subject.hasActiveKeyOrNoReceiverSigReq(true,
				EntityIdUtils.asTypedEvmAddress(payer), PRETEND_SENDER_ADDR, ledgers);

		assertTrue(payerFlag);

		verify(activationTest, never()).test(any(), any(), any());
	}


	@Test
	void createsValidityTestThatOnlyAcceptsContractIdKeyWhenBothRecipientAndContractAreActive() {
		final var uncontrolledId = EntityIdUtils.contractIdFromEvmAddress(Address.BLS12_G1ADD);
		final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_SENDER_ADDR);
		final var controlledKey = new JContractIDKey(controlledId);
		final var uncontrolledKey = new JContractIDKey(uncontrolledId);

		given(aliases.currentAddress(controlledId)).willReturn(PRETEND_SENDER_ADDR);
		given(aliases.currentAddress(uncontrolledId)).willReturn(PRETEND_TOKEN_ADDR);

		final var validityTestForNormalCall =
				subject.validityTestFor(
						false, PRETEND_SENDER_ADDR, aliases);
		final var validityTestForDelegateCall =
				subject.validityTestFor(
						true, PRETEND_SENDER_ADDR, aliases);

		assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
	}

	@Test
	void testsAccountAddressAndActiveContractIfEquals() {
		given(ledgers.accounts()).willReturn(accountsLedger);
		given(accountsLedger.getImmutableRef(smartContract)).willReturn(contract);

		final var verdict = subject.hasActiveKey(true,
				EntityIdUtils.asTypedEvmAddress(smartContract),
				EntityIdUtils.asTypedEvmAddress(smartContract), ledgers);

		assertTrue(verdict);
	}

	@Test
	void createsValidityTestThatAcceptsContractKeysWithJustRecipientActive() {
		final var uncontrolledId = EntityIdUtils.contractIdFromEvmAddress(Address.BLS12_G1ADD);
		final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_SENDER_ADDR);
		final var controlledKey = new JDelegatableContractIDKey(controlledId);
		final var uncontrolledKey = new JContractIDKey(uncontrolledId);
		final var otherControlledKey = new JContractAliasKey(0, 0, Address.BLS12_G1ADD.toArrayUnsafe());
		final var otherControlledId = otherControlledKey.getContractID();
		final var otherControlDelegateKey = new JDelegatableContractAliasKey(0, 0, Address.BLS12_G1ADD.toArrayUnsafe());

		given(aliases.currentAddress(controlledId)).willReturn(PRETEND_SENDER_ADDR);
		given(aliases.currentAddress(uncontrolledId)).willReturn(PRETEND_TOKEN_ADDR);
		given(aliases.currentAddress(otherControlledId)).willReturn(PRETEND_SENDER_ADDR);

		final var validityTestForNormalCall =
				subject.validityTestFor(
						false, PRETEND_SENDER_ADDR, aliases);
		final var validityTestForDelegateCall =
				subject.validityTestFor(
						true, PRETEND_SENDER_ADDR, aliases);

		assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
		assertTrue(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
		assertTrue(validityTestForNormalCall.test(otherControlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(otherControlledKey, INVALID_MISSING_SIG));
		assertTrue(validityTestForDelegateCall.test(otherControlDelegateKey, INVALID_MISSING_SIG));
	}

	@Test
	void validityTestsRelyOnCryptoValidityOtherwise() {
		final var mockSig = mock(TransactionSignature.class);
		final var mockKey = new JEd25519Key("01234567890123456789012345678901".getBytes());
		given(cryptoValidity.test(mockKey, mockSig)).willReturn(true);

		final var validityTestForNormalCall =
				subject.validityTestFor(
						false, PRETEND_SENDER_ADDR, aliases);
		final var validityTestForDelegateCall =
				subject.validityTestFor(
						true, PRETEND_SENDER_ADDR, aliases);

		assertTrue(validityTestForNormalCall.test(mockKey, mockSig));
		assertTrue(validityTestForDelegateCall.test(mockKey, mockSig));
	}

	private void givenAccessorInCtx() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);
	}
}
