package com.hedera.services.bdd.suites.contract.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.contracts.ParsingConstants;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.expandByteArrayTo32Length;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

public class TokenAndTypeCheckSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(TokenAndTypeCheckSuite.class);
    private static final String TOKEN_AND_TYPE_CHECK_CONTRACT = "TokenAndTypeCheck";
    private static final String ACCOUNT = "anybody";
    private static final int GAS_TO_OFFER = 4_000_000;
    private static final String GET_TOKEN_TYPE = "getType";
    private static final String IS_TOKEN = "isAToken";
    private static final String KEY = "key";

    public static void main(String... args) {
        new TokenAndTypeCheckSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(getTokenTypePrecompile());
    }

    private HapiApiSpec getTokenTypePrecompile() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("getTokenTypePrecompile")
                .given(
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        uploadInitCode(TOKEN_AND_TYPE_CHECK_CONTRACT),
                        contractCreate(TOKEN_AND_TYPE_CHECK_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCallLocal(
                                                        TOKEN_AND_TYPE_CHECK_CONTRACT,
                                                        GET_TOKEN_TYPE,
                                                        asAddress(vanillaTokenID.get()))
                                                )))

                .then();
//                        childRecordsCheck(
//                                "Tx",
//                                SUCCESS,
//                                recordWith()
//                                        .status(SUCCESS)
//                                        .contractCallResult(
//                                                resultWith()
//                                                        .contractCallResult(
//                                                                htsPrecompileResult()
//                                                                        .forFunction(
//                                                                                ParsingConstants.FunctionType
//                                                                                        .HAPI_GET_TOKEN_TYPE)
//                                                                        .withStatus(SUCCESS)))));
    }
}
