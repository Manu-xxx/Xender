package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.expandByteArrayTo32Length;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.FunctionType;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultTokenStatusSuite extends HapiApiSuite {
  private static final Logger log = LogManager.getLogger(DefaultTokenStatusSuite.class);
  private static final String TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT = "TokenDefaultKycAndFreezeStatus";
  private static final String ACCOUNT = "anybody";
  private static final String KYC_KEY = "kycKey";
  private static final String FREEZE_KEY = "freezeKey";
  private static final int GAS_TO_OFFER = 1_000_000;
  private static final String GET_TOKEN_DEFAULT_FREEZE = "getTokenDefaultFreeze";
  private static final String GET_TOKEN_DEFAULT_KYC = "getTokenDefaultKyc";

  public static void main(String... args) {
    new DefaultTokenStatusSuite().runSuiteSync();
  }

  @Override
  protected Logger getResultsLogger() {
    return log;
  }

  @Override
  public List<HapiApiSpec> getSpecsInSuite() {
    return List.of(
            getTokenDefaultFreezeStatus(),
            getTokenDefaultKycStatus()
        );
  }

  private HapiApiSpec getTokenDefaultFreezeStatus() {
    final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

    return defaultHapiSpec("GetTokenDefaultFreezeStatus")
        .given(
            newKeyNamed(FREEZE_KEY),
            cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
            cryptoCreate(TOKEN_TREASURY),
            tokenCreate(VANILLA_TOKEN)
                .tokenType(FUNGIBLE_COMMON)
                .treasury(TOKEN_TREASURY)
                .freezeDefault(true)
                .freezeKey(FREEZE_KEY)
                .initialSupply(1_000)
                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
            uploadInitCode(TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT),
            contractCreate(TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT)
        )
        .when(
            withOpContext(
                (spec, opLog) ->
                    allRunFor(
                        spec,
                        contractCall(
                            TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT,
                            GET_TOKEN_DEFAULT_FREEZE,
                            Tuple.singleton(
                                expandByteArrayTo32Length(
                            asAddress(vanillaTokenID.get()))))
                            .payingWith(ACCOUNT)
                            .via("GetTokenDefaultFreezeStatusTx")
                            .gas(GAS_TO_OFFER)
                      ))
        )
        .then(
            childRecordsCheck("GetTokenDefaultFreezeStatusTx", SUCCESS,
                recordWith()
                    .status(SUCCESS)
                    .contractCallResult(
                        resultWith()
                            .contractCallResult(
                                htsPrecompileResult()
                                    .forFunction(
                                        FunctionType
                                            .GET_TOKEN_DEFAULT_FREEZE_STATUS)
                                    .withStatus(SUCCESS)
                                    .withTokenDefaultFreezeStatus(true)))
                   )
         );

  }

  private HapiApiSpec getTokenDefaultKycStatus() {
    final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

    return defaultHapiSpec("GetTokenDefaultKycStatus")
        .given(
            newKeyNamed(KYC_KEY),
            cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
            cryptoCreate(TOKEN_TREASURY),
            tokenCreate(VANILLA_TOKEN)
                .tokenType(FUNGIBLE_COMMON)
                .treasury(TOKEN_TREASURY)
                .kycKey(KYC_KEY)
                .initialSupply(1_000)
                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
            uploadInitCode(TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT),
            contractCreate(TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT)
        )
        .when(
            withOpContext(
                (spec, opLog) ->
                    allRunFor(
                        spec,
                        contractCall(
                            TOKEN_DEFAULT_KYC_FREEZE_STATUS_CONTRACT,
                            GET_TOKEN_DEFAULT_KYC,
                            Tuple.singleton(
                                expandByteArrayTo32Length(
                            asAddress(vanillaTokenID.get()))))
                            .payingWith(ACCOUNT)
                            .via("GetTokenDefaultKycStatusTx")
                            .gas(GAS_TO_OFFER)
                    ))
        )
        .then(
            childRecordsCheck("GetTokenDefaultKycStatusTx", SUCCESS,
                recordWith()
                    .status(SUCCESS)
                    .contractCallResult(
                        resultWith()
                            .contractCallResult(
                                htsPrecompileResult()
                                    .forFunction(
                                        FunctionType
                                            .GET_TOKEN_DEFAULT_KYC_STATUS)
                                    .withStatus(SUCCESS)
                                    .withTokenDefaultKycStatus(false)))
            )
        );

  }

}
