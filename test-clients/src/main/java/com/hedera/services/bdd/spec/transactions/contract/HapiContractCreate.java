package com.hedera.services.bdd.spec.transactions.contract;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.equivAccount;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.solidityIdFrom;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCall.doGasLookup;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiContractCreate extends HapiBaseContractCreate<HapiContractCreate> {
	static final Key DEPRECATED_CID_ADMIN_KEY =
			Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(1_234L)).build();

	public HapiContractCreate(String contract) {
		super(contract);
	}

	public HapiContractCreate(String contract, String abi, Object... args) {
		super(contract, abi, args);
	}

	public HapiContractCreate exposingNumTo(LongConsumer obs) {
		newNumObserver = Optional.of(obs);
		return this;
	}

	public HapiContractCreate withExplicitParams(final Supplier<String> supplier) {
		explicitHexedParams = Optional.of(supplier);
		return this;
	}

	public HapiContractCreate proxy(String proxy) {
		this.proxy = Optional.of(proxy);
		return this;
	}

	public HapiContractCreate advertisingCreation() {
		advertiseCreation = true;
		return this;
	}

	@Override
	protected Key lookupKey(HapiApiSpec spec, String name) {
		return name.equals(contract) ? adminKey : spec.registry().getKey(name);
	}

	public HapiContractCreate exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
		this.gasObserver = Optional.of(gasObserver);
		return this;
	}

	public HapiContractCreate skipAccountRegistration() {
		shouldAlsoRegisterAsAccount = false;
		return this;
	}

	public HapiContractCreate uponSuccess(Consumer<HapiSpecRegistry> cb) {
		successCb = Optional.of(cb);
		return this;
	}

	public HapiContractCreate bytecode(String fileName) {
		bytecodeFile = Optional.of(fileName);
		return this;
	}

	public HapiContractCreate bytecode(Supplier<String> supplier) {
		bytecodeFileFn = Optional.of(supplier);
		return this;
	}

	public HapiContractCreate adminKey(KeyFactory.KeyType type) {
		adminKeyType = Optional.of(type);
		return this;
	}

	public HapiContractCreate adminKeyShape(SigControl controller) {
		adminKeyControl = Optional.of(controller);
		return this;
	}

	public HapiContractCreate autoRenewSecs(long period) {
		autoRenewPeriodSecs = Optional.of(period);
		return this;
	}

	public HapiContractCreate balance(long initial) {
		balance = Optional.of(initial);
		return this;
	}

	public HapiContractCreate gas(long amount) {
		gas = OptionalLong.of(amount);
		return this;
	}

	public HapiContractCreate entityMemo(String s) {
		memo = Optional.of(s);
		return this;
	}

	public HapiContractCreate omitAdminKey() {
		omitAdminKey = true;
		return this;
	}

	public HapiContractCreate immutable() {
		omitAdminKey = true;
		makeImmutable = true;
		return this;
	}

	public HapiContractCreate useDeprecatedAdminKey() {
		useDeprecatedAdminKey = true;
		return this;
	}

	public HapiContractCreate adminKey(String existingKey) {
		key = Optional.of(existingKey);
		return this;
	}


	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected HapiContractCreate self() {
		return this;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		if (!omitAdminKey && !useDeprecatedAdminKey) {
			generateAdminKey(spec);
		}
		if (bytecodeFileFn.isPresent()) {
			bytecodeFile = Optional.of(bytecodeFileFn.get().get());
		}
		if (!bytecodeFile.isPresent()) {
			setBytecodeToDefaultContract(spec);
		}
		Optional<byte[]> params;
		if (explicitHexedParams.isPresent()) {
			params = explicitHexedParams.map(Supplier::get).map(CommonUtils::unhex);
		} else {
			params = abi.isPresent()
					? Optional.of(CallTransaction.Function.fromJsonInterface(abi.get()).encodeArguments(args.get()))
					: Optional.empty();
		}
		FileID bytecodeFileId = TxnUtils.asFileId(bytecodeFile.get(), spec);
		ContractCreateTransactionBody opBody = spec
				.txns()
				.<ContractCreateTransactionBody, ContractCreateTransactionBody.Builder>body(
						ContractCreateTransactionBody.class, b -> {
							if (useDeprecatedAdminKey) {
								b.setAdminKey(DEPRECATED_CID_ADMIN_KEY);
							} else if (omitAdminKey) {
								if (makeImmutable) {
									b.setAdminKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()));
								}
							} else {
								b.setAdminKey(adminKey);
							}
							b.setFileID(bytecodeFileId);
							autoRenewPeriodSecs.ifPresent(p ->
									b.setAutoRenewPeriod(Duration.newBuilder().setSeconds(p).build()));
							balance.ifPresent(b::setInitialBalance);
							memo.ifPresent(b::setMemo);
							gas.ifPresent(b::setGas);
							proxy.ifPresent(p -> b.setProxyAccountID(asId(p, spec)));
							params.ifPresent(bytes -> b.setConstructorParameters(ByteString.copyFrom(bytes)));
						}
				);
		return b -> b.setContractCreateInstance(opBody);
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.ContractCreate,
				scFees::getContractCreateTxFeeMatrices, txn, numPayerSigs);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::createContract;
	}

	public long numOfCreatedContract() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> receipt.getContractID().getContractNum())
				.orElse(-1L);
	}
}
