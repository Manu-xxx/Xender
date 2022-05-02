package com.hedera.services.bdd.spec.transactions.contract;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.equivAccount;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.solidityIdFrom;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCall.doGasLookup;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public abstract class HapiBaseContractCreate <T extends HapiTxnOp<T>> extends HapiTxnOp<T> {

    static final Key MISSING_ADMIN_KEY = Key.getDefaultInstance();
    static final Logger log = LogManager.getLogger(HapiContractCreate.class);

    protected Key adminKey;
    protected boolean omitAdminKey = false;
    protected boolean makeImmutable = false;
    protected boolean advertiseCreation = false;
    protected boolean shouldAlsoRegisterAsAccount = true;
    protected boolean useDeprecatedAdminKey = false;
    protected final String contract;
    protected OptionalLong gas = OptionalLong.empty();
    Optional<String> key = Optional.empty();
    Optional<Long> autoRenewPeriodSecs = Optional.empty();
    Optional<Long> balance = Optional.empty();
    Optional<SigControl> adminKeyControl = Optional.empty();
    Optional<KeyFactory.KeyType> adminKeyType = Optional.empty();
    Optional<String> memo = Optional.empty();
    Optional<String> bytecodeFile = Optional.empty();
    Optional<Supplier<String>> bytecodeFileFn = Optional.empty();
    Optional<Consumer<HapiSpecRegistry>> successCb = Optional.empty();
    Optional<String> abi = Optional.empty();
    Optional<Object[]> args = Optional.empty();
    Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();
    Optional<LongConsumer> newNumObserver = Optional.empty();
    protected Optional<String> proxy = Optional.empty();
    protected Optional<Supplier<String>> explicitHexedParams = Optional.empty();

    protected HapiBaseContractCreate(String contract) {
        this.contract = contract;
    }

    protected HapiBaseContractCreate(String contract, String abi, Object... args) {
        this.contract = contract;
        this.abi = Optional.of(abi);
        this.args = Optional.of(args);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return (omitAdminKey || useDeprecatedAdminKey)
                ? super.defaultSigners()
                : List.of(spec -> spec.registry().getKey(effectivePayer(spec)), ignore -> adminKey);
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            if (gasObserver.isPresent()) {
                doGasLookup(gas -> gasObserver.get().accept(actualStatus, gas), spec, txnSubmitted, true);
            }
            return;
        }
        final var newId = lastReceipt.getContractID();
        newNumObserver.ifPresent(obs -> obs.accept(newId.getContractNum()));
        if (shouldAlsoRegisterAsAccount) {
            spec.registry().saveAccountId(contract, equivAccount(lastReceipt.getContractID()));
        }
        spec.registry().saveKey(contract, (omitAdminKey || useDeprecatedAdminKey) ? MISSING_ADMIN_KEY : adminKey);
        spec.registry().saveContractId(contract, newId);
        final var otherInfoBuilder = ContractGetInfoResponse.ContractInfo.newBuilder()
                .setContractAccountID(solidityIdFrom(lastReceipt.getContractID()))
                .setMemo(memo.orElse(spec.setup().defaultMemo()))
                .setAutoRenewPeriod(
                        Duration.newBuilder().setSeconds(
                                autoRenewPeriodSecs.orElse(spec.setup().defaultAutoRenewPeriod().getSeconds())).build());
        if (!omitAdminKey && !useDeprecatedAdminKey) {
            otherInfoBuilder.setAdminKey(adminKey);
        }
        final var otherInfo = otherInfoBuilder.build();
        spec.registry().saveContractInfo(contract, otherInfo);
        successCb.ifPresent(cb -> cb.accept(spec.registry()));
        if (advertiseCreation) {
            String banner = "\n\n" + bannerWith(
                    String.format(
                            "Created contract '%s' with id '0.0.%d'.",
                            contract,
                            lastReceipt.getContractID().getContractNum()));
            log.info(banner);
        }
        if (gasObserver.isPresent()) {
            doGasLookup(gas -> gasObserver.get().accept(SUCCESS, gas), spec, txnSubmitted, true);
        }
    }

    protected void generateAdminKey(HapiApiSpec spec) {
        if (key.isPresent()) {
            adminKey = spec.registry().getKey(key.get());
        } else {
            KeyGenerator generator = effectiveKeyGen();
            if (adminKeyControl.isEmpty()) {
                adminKey = spec.keys().generate(spec, adminKeyType.orElse(KeyFactory.KeyType.SIMPLE), generator);
            } else {
                adminKey = spec.keys().generateSubjectTo(spec, adminKeyControl.get(), generator);
            }
        }
    }

    protected void setBytecodeToDefaultContract(HapiApiSpec spec) throws Throwable {
        String implicitBytecodeFile = contract + "Bytecode";
        HapiFileCreate fileCreate = TxnVerbs
                .fileCreate(implicitBytecodeFile)
                .path(spec.setup().defaultContractPath());
        Optional<Throwable> opError = fileCreate.execFor(spec);
        if (opError.isPresent()) {
            throw opError.get();
        }
        bytecodeFile = Optional.of(implicitBytecodeFile);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper()
                .add("contract", contract);
        bytecodeFile.ifPresent(f -> helper.add("bytecode", f));
        memo.ifPresent(m -> helper.add("memo", m));
        autoRenewPeriodSecs.ifPresent(p -> helper.add("autoRenewPeriod", p));
        adminKeyControl.ifPresent(c -> helper.add("customKeyShape", Boolean.TRUE));
        Optional.ofNullable(lastReceipt)
                .ifPresent(receipt -> helper.add("created", receipt.getContractID().getContractNum()));
        return helper;
    }

    public long numOfCreatedContract() {
        return Optional
                .ofNullable(lastReceipt)
                .map(receipt -> receipt.getContractID().getContractNum())
                .orElse(-1L);
    }
}