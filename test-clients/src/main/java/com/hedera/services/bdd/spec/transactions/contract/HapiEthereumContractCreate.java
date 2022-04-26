package com.hedera.services.bdd.spec.transactions.contract;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getPrivateKeyFromSpec;
import static com.hedera.services.bdd.suites.HapiApiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;

public class HapiEthereumContractCreate extends HapiBaseContractCreate<HapiEthereumContractCreate> {
    private static final int BYTES_PER_KB = 1024;
    private static final int MAX_CALL_DATA_SIZE = 6 * BYTES_PER_KB;

    private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private EthTxData.EthTransactionType type;
    private byte[] chainId = Integers.toBytes(298);
    private long nonce;
    private long gasPrice = 20L;
    private long maxPriorityGas = 20_000L;
    private Optional<FileID> ethFileID = Optional.empty();
    private Optional<Long> maxGasAllowance = Optional.of(2_000_000L);
    private String privateKeyRef = SECP_256K1_SOURCE_KEY;

    public HapiEthereumContractCreate exposingNumTo(LongConsumer obs) {
        newNumObserver = Optional.of(obs);
        return this;
    }

    public HapiEthereumContractCreate withExplicitParams(final Supplier<String> supplier) {
        explicitHexedParams = Optional.of(supplier);
        return this;
    }

    public HapiEthereumContractCreate proxy(String proxy) {
        this.proxy = Optional.of(proxy);
        return this;
    }

    public HapiEthereumContractCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiEthereumContractCreate(String contract) {
       super(contract);
        this.payer = Optional.of(RELAYER);
    }

    public HapiEthereumContractCreate(String contract, String abi, Object... args) {
        super(contract, abi, args);
        this.payer = Optional.of(RELAYER);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.EthereumTransaction;
    }

    @Override
    protected HapiEthereumContractCreate self() {
        return this;
    }

    @Override
    protected Key lookupKey(HapiApiSpec spec, String name) {
        return name.equals(contract) ? adminKey : spec.registry().getKey(name);
    }

    public HapiEthereumContractCreate exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
        this.gasObserver = Optional.of(gasObserver);
        return this;
    }

    public HapiEthereumContractCreate skipAccountRegistration() {
        shouldAlsoRegisterAsAccount = false;
        return this;
    }

    public HapiEthereumContractCreate uponSuccess(Consumer<HapiSpecRegistry> cb) {
        successCb = Optional.of(cb);
        return this;
    }

    public HapiEthereumContractCreate bytecode(String fileName) {
        bytecodeFile = Optional.of(fileName);
        return this;
    }

    public HapiEthereumContractCreate bytecode(Supplier<String> supplier) {
        bytecodeFileFn = Optional.of(supplier);
        return this;
    }

    public HapiEthereumContractCreate adminKey(KeyFactory.KeyType type) {
        adminKeyType = Optional.of(type);
        return this;
    }

    public HapiEthereumContractCreate adminKeyShape(SigControl controller) {
        adminKeyControl = Optional.of(controller);
        return this;
    }

    public HapiEthereumContractCreate autoRenewSecs(long period) {
        autoRenewPeriodSecs = Optional.of(period);
        return this;
    }

    public HapiEthereumContractCreate balance(long initial) {
        balance = Optional.of(initial);
        return this;
    }

    public HapiEthereumContractCreate gas(long amount) {
        gas = OptionalLong.of(amount);
        return this;
    }

    public HapiEthereumContractCreate entityMemo(String s) {
        memo = Optional.of(s);
        return this;
    }

    public HapiEthereumContractCreate omitAdminKey() {
        omitAdminKey = true;
        return this;
    }

    public HapiEthereumContractCreate immutable() {
        omitAdminKey = true;
        makeImmutable = true;
        return this;
    }

    public HapiEthereumContractCreate useDeprecatedAdminKey() {
        useDeprecatedAdminKey = true;
        return this;
    }

    public HapiEthereumContractCreate adminKey(String existingKey) {
        key = Optional.of(existingKey);
        return this;
    }

    public HapiEthereumContractCreate maxGasAllowance(long maxGasAllowance) {
        this.maxGasAllowance = Optional.of(maxGasAllowance);
        return this;
    }

    public HapiEthereumContractCreate signingWith(String signingWith) {
        this.privateKeyRef = signingWith;
        return this;
    }

    public HapiEthereumContractCreate type(EthTxData.EthTransactionType type) {
        this.type = type;
        return this;
    }

    public HapiEthereumContractCreate nonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public HapiEthereumContractCreate gasPrice(long gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public HapiEthereumContractCreate maxPriorityGas(long maxPriorityGas) {
        this.maxPriorityGas = maxPriorityGas;
        return this;
    }

    public HapiEthereumContractCreate gasLimit(long gasLimit) {
        this.gas = OptionalLong.of(gasLimit);
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

        final var filePath = Utils.getResourcePath(bytecodeFile.get(), ".bin");
        final var fileContents = Utils.extractByteCode(filePath);

        byte[] callData = new byte[0];
        if(fileContents.toByteArray().length < MAX_CALL_DATA_SIZE) {
            callData = Bytes.fromHexString(new String(fileContents.toByteArray())).toArray();
        } else {
            ethFileID = Optional.of(TxnUtils.asFileId(bytecodeFile.get(), spec));
        }

        final var value = balance.isEmpty() ? BigInteger.ZERO : WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(balance.get()));
        final var longTuple = TupleType.parse("(int64)");
        final var gasPriceBytes = Bytes.wrap(longTuple.encode(Tuple.of(gasPrice)).array()).toArray();
        final var maxPriorityGasBytes = Bytes.wrap(longTuple.encode(Tuple.of(maxPriorityGas)).array()).toArray();
        final var gasBytes = gas.isEmpty() ? new byte[] {} : Bytes.wrap(longTuple.encode(Tuple.of(gas.getAsLong())).array()).toArray();

        final var ethTxData = new EthTxData(null, type, chainId, nonce, gasPriceBytes,
                maxPriorityGasBytes, gasBytes, gas.orElse(0L),
                new byte[]{}, value, callData, new byte[]{}, 0, null, null, null);

        byte[] privateKeyByteArray = getPrivateKeyFromSpec(spec, privateKeyRef);
        final var signedEthTxData = EthTxSigs.signMessage(ethTxData, privateKeyByteArray);

        EthereumTransactionBody opBody = spec
                .txns()
                .<EthereumTransactionBody, EthereumTransactionBody.Builder>body(
                        EthereumTransactionBody.class, builder -> {
                            builder.setEthereumData(ByteString.copyFrom(signedEthTxData.encodeTx()));
                            ethFileID.ifPresent(builder::setCallData);
                            maxGasAllowance.ifPresent(builder::setMaxGasAllowance);
                        }
                );
        return b -> b.setEthereumTransaction(opBody);
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
        return spec.fees().forActivityBasedOp(
                HederaFunctionality.EthereumTransaction,
                scFees::getEthereumTransactionFeeMatrices, txn, numPayerSigs);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::createContract;
    }
}
