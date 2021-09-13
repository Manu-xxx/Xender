package com.hedera.services.bdd.suites.contract;

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.swirlds.common.CommonUtils.hex;

public class ContractPerformanceSuite extends HapiApiSuite {
  private static final Logger LOG = LogManager.getLogger(ContractPerformanceSuite.class);

  private static final String PERF_RESOURCES = "src/main/resource/contract/performance/";
  private static final String LOADER_TEMPLATE =
      "608060405234801561001057600080fd5b5061%04x806100206000396000f3fe%s";
  private static final String RETURN_PROGRAM = "3360005260206000F3";
  private static final String REVERT_PROGRAM = "6055605555604360A052600160A0FD";

  private static final String EXTERNAL_CONTRACT_MARKER = "7465737420636f6e7472616374";
  private static final String RETURN_CONTRACT = "returnContract";
  private static final String RETURN_CONTRACT_ADDRESS = "72657475726e207465737420636f6e7472616374";
  private static final String REVERT_CONTRACT = "revertContract";
  private static final String REVERT_CONTRACT_ADDRESS = "726576657274207465737420636f6e7472616374";

  public static void main(String... args) {
    /* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
    //noinspection InstantiationOfUtilityClass
    new org.ethereum.crypto.HashUtil();

    new ContractPerformanceSuite().runSuiteAsync();
  }

  static HapiFileCreate createProgramFile(String name, String program) {
    return fileCreate(name).contents(String.format(LOADER_TEMPLATE, program.length(), program));
  }

  static HapiFileCreate createTestProgram(
      String test, ContractID returnAccountAddress, ContractID revertAccountAddress) {
    String path = PERF_RESOURCES + test;
    try {
      var contentString =
          new String(Files.toByteArray(new File(path)), StandardCharsets.US_ASCII)
              .replace(RETURN_CONTRACT_ADDRESS, hex(asSolidityAddress(returnAccountAddress)))
              .replace(REVERT_CONTRACT_ADDRESS, hex(asSolidityAddress(revertAccountAddress)));
      return fileCreate(test + "bytecode")
          .contents(contentString.getBytes(StandardCharsets.US_ASCII));
    } catch (Throwable t) {
      LOG.warn("createTestProgram for " + test + " failed to read bytes from '" + path + "'!", t);
      return fileCreate(test);
    }
  }

  @Override
  protected List<HapiApiSpec> getSpecsInSuite() {
    List<String> perfTests;
    try {
      perfTests =
          Files.readLines(
                  new File(PERF_RESOURCES + "performanceContracts.txt"), Charset.defaultCharset())
              .stream()
              .filter(s -> !s.isEmpty() && !s.startsWith("#"))
              .collect(Collectors.toList());
    } catch (IOException e) {
      return List.of();
    }
    List<HapiApiSpec> hapiSpecs = new ArrayList<>();
    for (String test : perfTests) {
      String path = PERF_RESOURCES + test;
      String contractCode;
      try {
        contractCode = new String(Files.toByteArray(new File(path)), StandardCharsets.US_ASCII);
      } catch (IOException e) {
        LOG.warn("createTestProgram for " + test + " failed to read bytes from '" + path + "'!", e);
        contractCode = "FE";
      }
      HapiSpecOperation[] givenBlock;
      if (contractCode.contains(EXTERNAL_CONTRACT_MARKER)) {
        givenBlock =
            new HapiSpecOperation[] {
              fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL),
              createProgramFile(RETURN_CONTRACT + "bytecode", RETURN_PROGRAM),
              contractCreate(RETURN_CONTRACT).bytecode(RETURN_CONTRACT + "bytecode"),
              createProgramFile(REVERT_CONTRACT + "bytecode", REVERT_PROGRAM),
              contractCreate(REVERT_CONTRACT).bytecode(REVERT_CONTRACT + "bytecode"),
              withOpContext(
                  (spec, opLog) ->
                      allRunFor(
                          spec,
                          createTestProgram(
                              test,
                              spec.registry().getContractId(RETURN_CONTRACT),
                              spec.registry().getContractId(REVERT_CONTRACT)))),
              contractCreate(test).bytecode(test + "bytecode")
            };
      } else {
        givenBlock =
            new HapiSpecOperation[] {
              fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL),
              fileCreate("bytecode").path(PERF_RESOURCES + test),
              contractCreate(test).bytecode("bytecode")
            };
      }
      hapiSpecs.add(
          defaultHapiSpec("Perf_" + test)
              .given(givenBlock)
              .when()
              .then(
                  withOpContext(
                      (spec, opLog) -> {
                        var contractCall = contractCall(test, "<empty>").gas(35000000);
                        allRunFor(spec, contractCall);
                        Assertions.assertEquals(
                            ResponseCodeEnum.SUCCESS, contractCall.getLastReceipt().getStatus());
                      })));
    }
    return hapiSpecs;
  }

  @Override
  protected Logger getResultsLogger() {
    return LOG;
  }
}
