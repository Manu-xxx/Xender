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
package com.hedera.services.bdd.suites.ethereum;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.createLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;

public class EthereumSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(EthereumSuite.class);
    private static final long depositAmount = 20_000L;
    private static final String CONTRACTS_MAX_GAS_PER_SEC = "contracts.maxGasPerSec";

    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String TOKEN_CREATE_CONTRACT = "NewTokenCreateContract";
    private static final String TOKEN_TRANSFER_CONTRACT = "TokenTransferContract";
    private static final String ERC721_CONTRACT = "NewERC721Contract";
    private static final String HELLO_WORLD_MINT_CONTRACT = "HelloWorldMint";
    private static final long GAS_LIMIT = 1_000_000;

    public static final String ERC20_CONTRACT = "ERC20Contract";
    public static final String EMIT_SENDER_ORIGIN_CONTRACT = "EmitSenderOrigin";
    private static final String ASSOCIATE_CONTRACT = "AssociateDissociate";

    private static final String FUNGIBLE_TOKEN = "fungibleToken";

    public static void main(String... args) {
        new EthereumSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return Stream.concat(Stream.of(setChainId()), Stream.of(debuggingLocalNodeIssue()))
                .toList();
    }

//    HapiApiSpec debuggingLocalNodeIssue() {
//
//        final AtomicReference<String> tokenCreateContractID = new AtomicReference<>();
//        final AtomicReference<String> tokenTransferContractID = new AtomicReference<>();
//        final AtomicReference<String> erc721ContractID = new AtomicReference<>();
//        final AtomicReference<Address> createdTokenAddress = new AtomicReference<>();
//
//        return defaultHapiSpec("Debugging Local Node Issue")
//                .given(
//                        /** Generate random ECDSA keys */
//                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
//                        newKeyNamed(SECP_256K1_RECEIVER_SOURCE_KEY).shape(SECP_256K1_SHAPE),
//                        /** Create the Relayer account */
//                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
//                        /** Create two ECDSA accounts via account auto-create transactions */
//                        cryptoTransfer(
//                                        tinyBarsFromAccountToAlias(
//                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS))
//                                .via("autoAccount"),
//                        cryptoTransfer(
//                                        tinyBarsFromAccountToAlias(
//                                                GENESIS,
//                                                SECP_256K1_RECEIVER_SOURCE_KEY,
//                                                ONE_HUNDRED_HBARS))
//                                .via("autoAccount2"),
//                        /** Upload contract bytecodes */
//                        createLargeFile(
//                                GENESIS,
//                                TOKEN_CREATE_CONTRACT,
//                                TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)),
//                        createLargeFile(
//                                GENESIS,
//                                TOKEN_TRANSFER_CONTRACT,
//                                TxnUtils.literalInitcodeFor(TOKEN_TRANSFER_CONTRACT)),
//                        createLargeFile(
//                                GENESIS,
//                                ERC721_CONTRACT,
//                                TxnUtils.literalInitcodeFor(ERC721_CONTRACT)),
//                        /** Deploy the contracts and expose their EVM Address Aliases */
//                        ethereumContractCreate(TOKEN_CREATE_CONTRACT)
//                                .type(EthTransactionType.EIP1559)
//                                .signingWith(SECP_256K1_SOURCE_KEY)
//                                .payingWith(RELAYER)
//                                .nonce(0)
//                                .bytecode(TOKEN_CREATE_CONTRACT)
//                                .gasPrice(10L)
//                                .maxGasAllowance(ONE_HUNDRED_HBARS)
//                                .gasLimit(1_000_000L)
//                                .hasKnownStatusFrom(SUCCESS),
//                        getContractInfo(TOKEN_CREATE_CONTRACT)
//                                .exposingEvmAddress(
//                                        address -> tokenCreateContractID.set("0x" + address)),
//                        ethereumContractCreate(TOKEN_TRANSFER_CONTRACT)
//                                .type(EthTransactionType.EIP1559)
//                                .signingWith(SECP_256K1_SOURCE_KEY)
//                                .payingWith(RELAYER)
//                                .nonce(1)
//                                .bytecode(TOKEN_TRANSFER_CONTRACT)
//                                .gasPrice(10L)
//                                .maxGasAllowance(ONE_HUNDRED_HBARS)
//                                .gasLimit(1_000_000L)
//                                .hasKnownStatusFrom(SUCCESS),
//                        getContractInfo(TOKEN_TRANSFER_CONTRACT)
//                                .exposingEvmAddress(
//                                        address -> tokenTransferContractID.set("0x" + address)),
//                        ethereumContractCreate(ERC721_CONTRACT)
//                                .type(EthTxData.EthTransactionType.EIP1559)
//                                .signingWith(SECP_256K1_SOURCE_KEY)
//                                .payingWith(RELAYER)
//                                .nonce(2)
//                                .bytecode(ERC721_CONTRACT)
//                                .gasPrice(10L)
//                                .maxGasAllowance(ONE_HUNDRED_HBARS)
//                                .gasLimit(1_000_000L)
//                                .hasKnownStatusFrom(SUCCESS),
//                        getContractInfo(ERC721_CONTRACT)
//                                .exposingEvmAddress(
//                                        address -> erc721ContractID.set("0x" + address)))
//                .when(
//                        withOpContext(
//                                (spec, opLog) -> {
//                                    /** Create HTS token via call to TokenCreateContract */
//                                    var createNFTPublicFunctionCall =
//                                            ethereumCall(
//                                                            TOKEN_CREATE_CONTRACT,
//                                                            "createNonFungibleTokenPublic",
//                                                            asHeadlongAddress(
//                                                                    tokenCreateContractID.get()))
//                                                    .type(EthTransactionType.EIP1559)
//                                                    .signingWith(SECP_256K1_SOURCE_KEY)
//                                                    .payingWith(RELAYER)
//                                                    .nonce(3)
//                                                    .gasPrice(10L)
//                                                    .sending(10000000000L)
//                                                    .gasLimit(1_000_000L)
//                                                    .via("createTokenTxn")
//                                                    .hasKnownStatusFrom(
//                                                            CONTRACT_REVERT_EXECUTED, SUCCESS);
//                                    /**
//                                     * Save the created token address exposed through the txn record
//                                     */
//                                    var getNewTokenAddressFromCreateRecord =
//                                            getTxnRecord("createTokenTxn")
//                                                    .exposingFilteredCallResultVia(
//                                                            getABIForContract(
//                                                                    TOKEN_CREATE_CONTRACT),
//                                                            "CreatedToken",
//                                                            data ->
//                                                                    createdTokenAddress.set(
//                                                                            (Address) data.get(0)));
//
//                                    allRunFor(
//                                            spec,
//                                            createNFTPublicFunctionCall,
//                                            getNewTokenAddressFromCreateRecord);
//                                }),
//                        withOpContext(
//                                (spec, opLog) -> {
//                                    /**
//                                     * Mint an NFT via call to token create contract mint function
//                                     */
//                                    var mintTokenPublicFunctionCall =
//                                            ethereumCall(
//                                                            TOKEN_CREATE_CONTRACT,
//                                                            "mintTokenPublic",
//                                                            createdTokenAddress.get(),
//                                                            BigInteger.ZERO,
//                                                            new byte[][] {new byte[] {(byte) 0x01}})
//                                                    .type(EthTransactionType.EIP1559)
//                                                    .signingWith(SECP_256K1_SOURCE_KEY)
//                                                    .payingWith(RELAYER)
//                                                    .nonce(4)
//                                                    .gasPrice(10L)
//                                                    .gasLimit(1_000_000L)
//                                                    .via("mintTokenTxn")
//                                                    .hasKnownStatusFrom(
//                                                            CONTRACT_REVERT_EXECUTED, SUCCESS);
//                                    allRunFor(spec, mintTokenPublicFunctionCall);
//                                }))
//                .then(
//                        withOpContext(
//                                (spec, opLog) -> {
//                                    // todo get aliased account info
//                                    //                                    System.out.println(
//                                    //                                            "*** acc address:
//                                    // "
//                                    //                                                    +
//                                    // Address.wrap(
//                                    //
//                                    // "0x"
//                                    //
//                                    //      + ByteString.copyFrom(
//                                    //
//                                    //              Objects.requireNonNull(
//                                    //
//                                    //                      EthTxSigs
//                                    //
//                                    //                              .recoverAddressFromPubKey(
//                                    //
//                                    //                                      spec.registry()
//                                    //
//                                    //                                              .getKey(
//                                    //
//                                    //
//                                    // SECP_256K1_SOURCE_KEY)
//                                    //
//                                    //
//                                    // .getECDSASecp256K1()
//                                    //
//                                    //
//                                    // .toByteArray())))));
//                                    /**
//                                     * Transfer the minted NFT from the created contract address to
//                                     * the account alias address
//                                     */
//                                    var transferNFTPublicCall =
//                                            ethereumCall(
//                                                            TOKEN_TRANSFER_CONTRACT,
//                                                            "transferNFTPublic",
//                                                            createdTokenAddress.get(),
//                                                            // todo created contract address
//                                                            // todo account alias address
//                                                            new byte[][] {new byte[] {(byte) 0x01}})
//                                                    .type(EthTxData.EthTransactionType.EIP1559)
//                                                    .signingWith(SECP_256K1_SOURCE_KEY)
//                                                    .payingWith(RELAYER)
//                                                    .nonce(5)
//                                                    .gasPrice(10L)
//                                                    .gasLimit(1_000_000L)
//                                                    .via("transferNFTTxn")
//                                                    .hasKnownStatusFrom(
//                                                            CONTRACT_REVERT_EXECUTED, SUCCESS);
//                                    //                                    allRunFor(spec,
//                                    // transferNFTPublicCall);
//                                }));
//    }

  HapiApiSpec debuggingLocalNodeIssue() {

    final AtomicReference<String> tokenCreateContractID = new AtomicReference<>();
    final AtomicReference<String> tokenTransferContractID = new AtomicReference<>();
    final AtomicReference<String> erc721ContractID = new AtomicReference<>();
    final AtomicReference<Address> createdTokenAddress = new AtomicReference<>();
    final AtomicReference<ByteString> createdTokenAddressString = new AtomicReference<>();
    final String nft = "nft";
    final String treasury = "treasury";
    final String spender = "spender";
    final var createTokenNum = new AtomicLong();
    final var createTokenContractNum = new AtomicLong();
    return defaultHapiSpec("Debugging Local Node Issue")
        .given(
            /** Generate random ECDSA keys */
            newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(SECP_256K1_RECEIVER_SOURCE_KEY).shape(SECP_256K1_SHAPE),
            /** Create the Relayer account */
            cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
            cryptoCreate(spender),
            /** Create the Treasury account */

            /** Create two ECDSA accounts via account auto-create transactions */
            cryptoTransfer(
                tinyBarsFromAccountToAlias(
                    GENESIS, SECP_256K1_SOURCE_KEY, ONE_MILLION_HBARS))
                .via("autoAccount"),
            cryptoTransfer(
                tinyBarsFromAccountToAlias(
                    GENESIS,
                    SECP_256K1_RECEIVER_SOURCE_KEY,
                    ONE_HUNDRED_HBARS))
                .via("autoAccount2"),
            /** Upload contract bytecodes */
            createLargeFile(
                GENESIS,
                TOKEN_CREATE_CONTRACT,
                TxnUtils.literalInitcodeFor(TOKEN_CREATE_CONTRACT)),
            createLargeFile(
                GENESIS,
                TOKEN_TRANSFER_CONTRACT,
                TxnUtils.literalInitcodeFor(TOKEN_TRANSFER_CONTRACT)),
//uploadInitCode(ASSOCIATE_CONTRACT),
//contractCreate(ASSOCIATE_CONTRACT),

//            createLargeFile(
//                GENESIS,
//                ERC721_CONTRACT,
//                TxnUtils.literalInitcodeFor(ERC721_CONTRACT)),
            /** Create NFT*/
//            tokenCreate(nft).tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
//                .treasury(treasury)
//                .initialSupply(0L)
//                .supplyKey(MULTI_KEY)
//                .exposingCreatedIdTo(),
            /** Mint the NFT*/
//            mintToken(
//                nft,
//                List.of(
//                    ByteStringUtils.wrapUnsafely("meta1".getBytes()),
//                    ByteStringUtils.wrapUnsafely("meta2".getBytes()))),
            /** Deploy the contracts and expose their EVM Address Aliases */
            ethereumContractCreate(TOKEN_CREATE_CONTRACT)
                .type(EthTransactionType.EIP1559)
                .signingWith(SECP_256K1_SOURCE_KEY)
                .payingWith(RELAYER)
                .nonce(0)
                .bytecode(TOKEN_CREATE_CONTRACT)
                .gasPrice(10L)
                .maxGasAllowance(ONE_HUNDRED_HBARS)
                .gasLimit(1_000_000L)
                .gas(1_000_000L)
                .hasKnownStatusFrom(SUCCESS)
                .exposingNumTo(createTokenContractNum::set),
            getContractInfo(TOKEN_CREATE_CONTRACT)
                .exposingEvmAddress(
                    tokenCreateContractID::set),
            ethereumContractCreate(TOKEN_TRANSFER_CONTRACT)
                .type(EthTransactionType.EIP1559)
                .signingWith(SECP_256K1_SOURCE_KEY)
                .payingWith(RELAYER)
                .nonce(1)
                .bytecode(TOKEN_TRANSFER_CONTRACT)
                .gasPrice(10L)
                .maxGasAllowance(ONE_HUNDRED_HBARS)
                .gasLimit(1_000_000L)
                .hasKnownStatusFrom(SUCCESS),
            getContractInfo(TOKEN_TRANSFER_CONTRACT)
                .exposingEvmAddress(
                    tokenTransferContractID::set))
//            ethereumContractCreate(ERC721_CONTRACT, asHeadlongAddress())
//                .type(EthTxData.EthTransactionType.EIP1559)
//                .signingWith(SECP_256K1_SOURCE_KEY)
//                .payingWith(RELAYER)
//                .nonce(2)
//                .bytecode(ERC721_CONTRACT)
//                .gasPrice(10L)
//                .maxGasAllowance(ONE_HUNDRED_HBARS)
//                .gasLimit(1_000_000L)
//                .hasKnownStatusFrom(SUCCESS),
//            getContractInfo(ERC721_CONTRACT)
//                .exposingEvmAddress(
//                    address -> erc721ContractID.set("0x" + address))
//        )
        .when(
            withOpContext(
                (spec, opLog) -> {
                  /** Create HTS token via call to TokenCreateContract */
                  var createNFTPublicFunctionCall =
                      ethereumCall(
                          TOKEN_CREATE_CONTRACT,
                          "createNonFungibleTokenPublic",
//                          asHeadlongAddress(
//                              tokenCreateContractID.get()
                          asHeadlongAddress(
                              tokenCreateContractID
                                      .get())
                          )
                          .type(EthTransactionType.EIP1559)
                          .signingWith(SECP_256K1_SOURCE_KEY)
                          .payingWith(RELAYER)
                          .nonce(2)
                          .gasPrice(10L)
                          .sending(10000000000L)
                          .gasLimit(1_000_000L)
                          .via("createTokenTxn")
                          .hasKnownStatusFrom(
                              CONTRACT_REVERT_EXECUTED, SUCCESS)
//                          .exposingResultTo(
//                              result -> {
//                                log.info(
//                                    EXPLICIT_CREATE_RESULT,
//                                    result[0]);
//                                final var res =
//                                    (Address) result[0];
//                                createTokenNum.set(
//                                    res.value()
//                                        .longValueExact());
//                              })
        ;

                  /**
                   * Save the created token address exposed through the txn record
                   */
//                  var getNewTokenAddressFromCreateRecord =
//                      getTxnRecord("createTokenTxn")
////                          .andAllChildRecords().logged()
//                          .exposingFilteredCallResultVia(
//                              getABIForContract(
//                                  TOKEN_CREATE_CONTRACT),
//                              "CreatedToken",
//                              data ->
//                                  createdTokenAddress.set(
//                                      (Address) data.get(0)));
//
                  final var setCreatedToken = getTxnRecord("createTokenTxn")
                      .hasPriority(
                          recordWith()
                              .contractCallResult(resultWith().exposeCreatedTokenAddress(data -> createdTokenAddressString.set(data))));


                  allRunFor(
                      spec,
                      createNFTPublicFunctionCall,
//                     , getNewTokenAddressFromCreateRecord,
                      setCreatedToken
                  );

//                  var uploadEthereumContract = uploadInitCodeWithAddressConstructorArguments(ERC721_CONTRACT,
////                      asHeadlongAddress(Bytes.wrap(stripFirst12Bytes(CommonUtils.unhex(createdTokenAddressString.get().toString()))).toHexString())
////                      asHeadlongAddress(Bytes.wrap(createdTokenAddressString.get().toByteArray()).toHexString())
//                      stripFirst12Bytes(createdTokenAddressString.get().toByteArray())
//                     );


                  var uploadEthereumContract = uploadInitCode(ERC721_CONTRACT);


                  allRunFor(
                      spec
                      ,uploadEthereumContract
                  );

                  var createEthereumContract = ethereumContractCreate(ERC721_CONTRACT)
                      .type(EthTxData.EthTransactionType.EIP1559)
                      .signingWith(SECP_256K1_SOURCE_KEY)
                      .payingWith(RELAYER)
                      .nonce(3)
//                      .bytecode(ERC721_CONTRACT)
                      .gasPrice(10L)
                      .maxGasAllowance(ONE_HUNDRED_HBARS)
                      .gasLimit(1_000_000L)
                      .hasKnownStatusFrom(SUCCESS);
//
                      var exposeEthereumContractAddress = getContractInfo(ERC721_CONTRACT)
                          .exposingEvmAddress(
                              address -> erc721ContractID.set("0x" + address));

                  var setAddress =
                      ethereumCall(
                          ERC721_CONTRACT,
                          "setAddress",
                          asHeadlongAddress(
                              Bytes.wrap(createdTokenAddressString.get().toByteArray()).toHexString()))
                          .type(EthTransactionType.EIP1559)
                          .signingWith(SECP_256K1_SOURCE_KEY)
                          .payingWith(RELAYER)
                          .nonce(4)
                          .gasPrice(10L)
                          .gasLimit(1_000_000L)
                          .via("setAddressTxn")
                          .hasKnownStatusFrom(SUCCESS);



                  allRunFor(
                      spec
                      ,createEthereumContract
                      ,exposeEthereumContractAddress
                      ,setAddress
                      );
                }),
            withOpContext(
                (spec, opLog) -> {

                  var associateToken =
                      ethereumCall(
                          ERC721_CONTRACT,
                          "associateTokenPublic",
                          asHeadlongAddress(erc721ContractID.get()),
                          asHeadlongAddress(
                              Bytes.wrap(createdTokenAddressString.get().toByteArray()).toHexString()))
                          .type(EthTransactionType.EIP1559)
                          .signingWith(SECP_256K1_SOURCE_KEY)
                          .payingWith(GENESIS)
                          .nonce(5)
                          .gasPrice(10L)
                          .gasLimit(1_000_000L)
                          .via("associateTokenTxn")
                          .hasKnownStatusFrom(SUCCESS);
                  var approveForAllCall =
                      ethereumCall(
                          ERC721_CONTRACT,
                          "setApprovalForAll",
                          asHeadlongAddress(asAddress(spec.registry().getAccountID(spender))),
                          true)
                          .type(EthTransactionType.EIP1559)
                          .signingWith(SECP_256K1_SOURCE_KEY)
                          .payingWith(RELAYER)
                          .nonce(6)
                          .gasPrice(10L)
                          .gasLimit(1_000_000L)
                          .via("setApproveForAllTxn")
                          .hasKnownStatusFrom(SUCCESS).logged();
//
                  var isApprovedForAll =
                      ethereumCall(
                          ERC721_CONTRACT,
                          "isApprovedForAll",
                          asHeadlongAddress(tokenCreateContractID.get()),
                          asHeadlongAddress(asAddress(spec.registry().getAccountID(spender))))
                          .type(EthTransactionType.EIP1559)
                          .signingWith(SECP_256K1_SOURCE_KEY)
                          .payingWith(RELAYER)
                          .nonce(7)
                          .gasPrice(10L)
                          .gasLimit(1_000_000L)
                          .via("isApprovedForAllTxn")
                          .hasKnownStatusFrom(SUCCESS).logged();

                  var isApprovedForAllRecord = getTxnRecord("isApprovedForAllTxn").andAllChildRecords().logged();

                  allRunFor(spec
                      ,associateToken
                      ,approveForAllCall
                      , isApprovedForAll
                      ,isApprovedForAllRecord
                  );
                }))
        .then(
            withOpContext(
                (spec, opLog) -> {
                }));
  }

    HapiApiSpec ETX_010_transferToCryptoAccountSucceeds() {
        String RECEIVER = "RECEIVER";
        final String aliasBalanceSnapshot = "aliasBalance";
        return defaultHapiSpec("ETX_010_transferToCryptoAccountSucceeds")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RECEIVER).balance(0L),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords())
                .when(
                        balanceSnapshot(aliasBalanceSnapshot, SECP_256K1_SOURCE_KEY)
                                .accountIsAlias(),
                        ethereumCryptoTransfer(RECEIVER, FIVE_HBARS)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(0L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(2_000_000L)
                                .via("payTxn")
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord("payTxn")
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(1L)),
                        getAccountBalance(RECEIVER).hasTinyBars(FIVE_HBARS),
                        getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                .hasTinyBars(
                                        changeFromSnapshot(aliasBalanceSnapshot, -FIVE_HBARS)));
    }

    List<HapiApiSpec> feePaymentMatrix() {
        final long gasPrice = 71;
        final long chargedGasLimit = GAS_LIMIT * 4 / 5;

        final long noPayment = 0L;
        final long thirdOfFee = gasPrice / 3;
        final long thirdOfPayment = thirdOfFee * chargedGasLimit;
        final long thirdOfLimit = thirdOfFee * GAS_LIMIT;
        final long fullAllowance = gasPrice * chargedGasLimit * 5 / 4;
        final long fullPayment = gasPrice * chargedGasLimit;
        final long ninetyPercentFee = gasPrice * 9 / 10;

        return Stream.of(
                        new Object[] {false, noPayment, noPayment, noPayment},
                        new Object[] {false, noPayment, thirdOfPayment, noPayment},
                        new Object[] {true, noPayment, fullAllowance, noPayment},
                        new Object[] {false, thirdOfFee, noPayment, noPayment},
                        new Object[] {false, thirdOfFee, thirdOfPayment, noPayment},
                        new Object[] {true, thirdOfFee, fullAllowance, thirdOfLimit},
                        new Object[] {true, thirdOfFee, fullAllowance * 9 / 10, thirdOfLimit},
                        new Object[] {false, ninetyPercentFee, noPayment, noPayment},
                        new Object[] {true, ninetyPercentFee, thirdOfPayment, fullPayment},
                        new Object[] {true, gasPrice, noPayment, fullPayment},
                        new Object[] {true, gasPrice, thirdOfPayment, fullPayment},
                        new Object[] {true, gasPrice, fullAllowance, fullPayment})
                .map(
                        params ->
                                // [0] - success
                                // [1] - sender gas price
                                // [2] - relayer offered
                                // [3] - sender charged amount
                                // relayer charged amount can easily be calculated via
                                // wholeTransactionFee - senderChargedAmount
                                matrixedPayerRelayerTest(
                                        (boolean) params[0],
                                        (long) params[1],
                                        (long) params[2],
                                        (long) params[3]))
                .toList();
    }

    HapiApiSpec matrixedPayerRelayerTest(
            final boolean success,
            final long senderGasPrice,
            final long relayerOffered,
            final long senderCharged) {
        return defaultHapiSpec(
                        "feePaymentMatrix "
                                + (success ? "Success/" : "Failure/")
                                + senderGasPrice
                                + "/"
                                + relayerOffered)
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when()
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final String senderBalance = "senderBalance";
                                    final String payerBalance = "payerBalance";
                                    final var subop1 =
                                            balanceSnapshot(senderBalance, SECP_256K1_SOURCE_KEY)
                                                    .accountIsAlias();
                                    final var subop2 = balanceSnapshot(payerBalance, RELAYER);
                                    final var subop3 =
                                            ethereumCall(
                                                            PAY_RECEIVABLE_CONTRACT,
                                                            "deposit",
                                                            BigInteger.valueOf(depositAmount))
                                                    .type(EthTxData.EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .via("payTxn")
                                                    .nonce(0)
                                                    .maxGasAllowance(relayerOffered)
                                                    .maxFeePerGas(senderGasPrice)
                                                    .gasLimit(GAS_LIMIT)
                                                    .sending(depositAmount)
                                                    .hasKnownStatus(
                                                            success
                                                                    ? ResponseCodeEnum.SUCCESS
                                                                    : ResponseCodeEnum
                                                                            .INSUFFICIENT_TX_FEE);

                                    final HapiGetTxnRecord hapiGetTxnRecord =
                                            getTxnRecord("payTxn").logged();
                                    allRunFor(spec, subop1, subop2, subop3, hapiGetTxnRecord);

                                    final long wholeTransactionFee =
                                            hapiGetTxnRecord
                                                    .getResponseRecord()
                                                    .getTransactionFee();
                                    final var subop4 =
                                            getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    senderBalance,
                                                                    success
                                                                            ? (-depositAmount
                                                                                    - senderCharged)
                                                                            : 0));
                                    final var subop5 =
                                            getAccountBalance(RELAYER)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    payerBalance,
                                                                    success
                                                                            ? -(wholeTransactionFee
                                                                                    - senderCharged)
                                                                            : -wholeTransactionFee));
                                    allRunFor(spec, subop4, subop5);
                                }));
    }

    HapiApiSpec setChainId() {
        return defaultHapiSpec("SetChainId").given().when().then(overriding(CHAIN_ID_PROP, "298"));
    }

    HapiApiSpec invalidTxData() {
        return defaultHapiSpec("InvalidTxData")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .invalidateEthereumData()
                                .gasLimit(1_000_000L)
                                .hasPrecheck(INVALID_ETHEREUM_TRANSACTION)
                                .via("payTxn"))
                .then();
    }

    HapiApiSpec ETX_014_contractCreateInheritsSignerProperties() {
        final AtomicReference<String> contractID = new AtomicReference<>();
        final String MEMO = "memo";
        final String PROXY = "proxy";
        final long INITIAL_BALANCE = 100L;
        final long AUTO_RENEW_PERIOD = THREE_MONTHS_IN_SECONDS + 60;
        return defaultHapiSpec("ContractCreateInheritsProperties")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        cryptoCreate(PROXY))
                .when(
                        cryptoUpdateAliased(SECP_256K1_SOURCE_KEY)
                                .autoRenewPeriod(AUTO_RENEW_PERIOD)
                                .entityMemo(MEMO)
                                .payingWith(GENESIS)
                                .signedBy(SECP_256K1_SOURCE_KEY, GENESIS),
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .balance(INITIAL_BALANCE)
                                .gasPrice(10L)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .exposingNumTo(
                                        num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "getBalance")
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(1L)
                                .gasPrice(10L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS))
                .then(
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged(),
                        sourcing(
                                () ->
                                        getContractInfo(contractID.get())
                                                .logged()
                                                .has(
                                                        ContractInfoAsserts.contractWith()
                                                                .defaultAdminKey()
                                                                .autoRenew(AUTO_RENEW_PERIOD)
                                                                .balance(INITIAL_BALANCE)
                                                                .memo(MEMO))));
    }

    HapiApiSpec ETX_031_invalidNonceEthereumTxFailsAndChargesRelayer() {
        final var relayerSnapshot = "relayer";
        final var senderSnapshot = "sender";
        return defaultHapiSpec("ETX_031_invalidNonceEthereumTxFailsAndChargesRelayer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        balanceSnapshot(relayerSnapshot, RELAYER),
                        balanceSnapshot(senderSnapshot, SECP_256K1_SOURCE_KEY).accountIsAlias(),
                        ethereumCall(
                                        PAY_RECEIVABLE_CONTRACT,
                                        "deposit",
                                        BigInteger.valueOf(depositAmount))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(999L)
                                .via("payTxn")
                                .hasKnownStatus(ResponseCodeEnum.WRONG_NONCE))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var payTxn =
                                            getTxnRecord("payTxn")
                                                    .logged()
                                                    .hasPriority(
                                                            recordWith()
                                                                    .ethereumHash(
                                                                            ByteString.copyFrom(
                                                                                    spec.registry()
                                                                                            .getBytes(
                                                                                                    ETH_HASH_KEY))));
                                    allRunFor(spec, payTxn);
                                    final var fee = payTxn.getResponseRecord().getTransactionFee();
                                    final var relayerBalance =
                                            getAccountBalance(RELAYER)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(
                                                                    relayerSnapshot, -fee));
                                    final var senderBalance =
                                            getAutoCreatedAccountBalance(SECP_256K1_SOURCE_KEY)
                                                    .hasTinyBars(
                                                            changeFromSnapshot(senderSnapshot, 0));
                                    allRunFor(spec, relayerBalance, senderBalance);
                                }),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(0L)));
    }

    HapiApiSpec accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation() {
        final String ACCOUNT = "account";
        return defaultHapiSpec(
                        "ETX_026_accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation")
                .given(
                        UtilVerbs.overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, "false"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY).balance(ONE_HUNDRED_HBARS))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(ACCOUNT)
                                .maxGasAllowance(FIVE_HBARS)
                                .nonce(0)
                                .gasLimit(GAS_LIMIT)
                                .hasKnownStatus(INVALID_ACCOUNT_ID))
                .then(UtilVerbs.resetToDefault(CRYPTO_CREATE_WITH_ALIAS_ENABLED));
    }

    HapiApiSpec ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        return defaultHapiSpec("ETX_012_precompileCallSucceedsWhenNeededSignatureInEthTxn")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(1_000_000L)
                                .via(mintTxn)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))));
    }

    HapiApiSpec ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        final String MULTI_KEY = "MULTI_KEY";
        return defaultHapiSpec("ETX_013_precompileCallSucceedsWhenNeededSignatureInHederaTxn")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .alsoSigningWithFullPrefix(MULTI_KEY)
                                .nonce(0)
                                .gasPrice(50L)
                                .maxGasAllowance(FIVE_HBARS)
                                .gasLimit(1_000_000L)
                                .via(mintTxn)
                                .hasKnownStatus(SUCCESS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .status(SUCCESS)
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))));
    }

    HapiApiSpec ETX_013_precompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn() {
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        final String fungibleToken = "token";
        final String mintTxn = "mintTxn";
        final String MULTI_KEY = "MULTI_KEY";
        return defaultHapiSpec(
                        "ETX_013_precompileCallFailsWhenSignatureMissingFromBothEthereumAndHederaTxn")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(HELLO_WORLD_MINT_CONTRACT),
                        tokenCreate(fungibleToken)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))))
                .when(
                        sourcing(
                                () ->
                                        contractCreate(
                                                HELLO_WORLD_MINT_CONTRACT,
                                                asHeadlongAddress(asAddress(fungible.get())))),
                        ethereumCall(HELLO_WORLD_MINT_CONTRACT, "brrr", BigInteger.valueOf(5))
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .nonce(0)
                                .via(mintTxn)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord(mintTxn)
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder())
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        childRecordsCheck(
                                mintTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    HapiApiSpec ETX_009_callsToTokenAddresses() {
        final AtomicReference<String> tokenNum = new AtomicReference<>();
        final var totalSupply = 50;

        return defaultHapiSpec("CallsToTokenAddresses")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoTransfer(
                                tinyBarsFromAccountToAlias(
                                        GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(totalSupply)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(SECP_256K1_SOURCE_KEY)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingCreatedIdTo(tokenNum::set),
                        uploadInitCode(ERC20_CONTRACT),
                        contractCreate(ERC20_CONTRACT).adminKey(THRESHOLD))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    allRunFor(
                                            spec,
                                            ethereumCallWithFunctionAbi(
                                                            true,
                                                            FUNGIBLE_TOKEN,
                                                            getABIFor(
                                                                    Utils.FunctionType.FUNCTION,
                                                                    "totalSupply",
                                                                    "ERC20ABI"))
                                                    .type(EthTxData.EthTransactionType.EIP1559)
                                                    .signingWith(SECP_256K1_SOURCE_KEY)
                                                    .payingWith(RELAYER)
                                                    .via("totalSupplyTxn")
                                                    .nonce(0)
                                                    .gasPrice(50L)
                                                    .maxGasAllowance(FIVE_HBARS)
                                                    .maxPriorityGas(2L)
                                                    .gasLimit(1_000_000L)
                                                    .hasKnownStatus(ResponseCodeEnum.SUCCESS));
                                }))
                .then(
                        childRecordsCheck(
                                "totalSupplyTxn",
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                FunctionType
                                                                                        .ERC_TOTAL_SUPPLY)
                                                                        .withTotalSupply(
                                                                                totalSupply)))));
    }

    // ETX-011 and ETX-030
    HapiApiSpec originAndSenderAreEthereumSigner() {
        return defaultHapiSpec("originAndSenderAreEthereumSigner")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(EMIT_SENDER_ORIGIN_CONTRACT),
                        contractCreate(EMIT_SENDER_ORIGIN_CONTRACT))
                .when(
                        ethereumCall(EMIT_SENDER_ORIGIN_CONTRACT, "logNow")
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxFeePerGas(50L)
                                .gasLimit(1_000_000L)
                                .via("payTxn")
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(
                        withOpContext(
                                (spec, ignore) ->
                                        allRunFor(
                                                spec,
                                                getTxnRecord("payTxn")
                                                        .logged()
                                                        .hasPriority(
                                                                recordWith()
                                                                        .contractCallResult(
                                                                                resultWith()
                                                                                        .logs(
                                                                                                inOrder(
                                                                                                        logWith()
                                                                                                                .ecdsaAliasStartingAt(
                                                                                                                        SECP_256K1_SOURCE_KEY,
                                                                                                                        12)
                                                                                                                .ecdsaAliasStartingAt(
                                                                                                                        SECP_256K1_SOURCE_KEY,
                                                                                                                        44)
                                                                                                                .withTopicsInOrder(
                                                                                                                        List
                                                                                                                                .of(
                                                                                                                                        eventSignatureOf(
                                                                                                                                                "Info(address,address)")))))
                                                                                        .senderId(
                                                                                                spec.registry()
                                                                                                        .getAccountID(
                                                                                                                spec.registry()
                                                                                                                        .aliasIdFor(
                                                                                                                                SECP_256K1_SOURCE_KEY)
                                                                                                                        .getAlias()
                                                                                                                        .toStringUtf8())))
                                                                        .ethereumHash(
                                                                                ByteString.copyFrom(
                                                                                        spec.registry()
                                                                                                .getBytes(
                                                                                                        ETH_HASH_KEY)))))),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).has(accountWith().nonce(1L)));
    }

    private HapiApiSpec ETX_008_contractCreateExecutesWithExpectedRecord() {
        final var txn = "creation";
        final var contract = "Fuse";

        return defaultHapiSpec("ETX_008_contractCreateExecutesWithExpectedRecord")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var op = getTxnRecord(txn);
                                    allRunFor(spec, op);
                                    final var record = op.getResponseRecord();
                                    final var creationResult = record.getContractCreateResult();
                                    final var createdIds =
                                            creationResult.getCreatedContractIDsList();
                                    assertEquals(
                                            4,
                                            createdIds.size(),
                                            "Expected four creations but got " + createdIds);
                                }))
                .when()
                .then();
    }

    private HapiApiSpec ETX_007_fungibleTokenCreateWithFeesHappyPath() {
        final var createdTokenNum = new AtomicLong();
        final var feeCollector = "feeCollector";
        final var contract = "TokenCreateContract";
        final var EXISTING_TOKEN = "EXISTING_TOKEN";
        final var firstTxn = "firstCreateTxn";
        final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;

        return defaultHapiSpec("ETX_007_fungibleTokenCreateWithFeesHappyPath")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        cryptoCreate(feeCollector).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(contract),
                        contractCreate(contract).gas(GAS_LIMIT),
                        tokenCreate(EXISTING_TOKEN).decimals(5),
                        tokenAssociate(feeCollector, EXISTING_TOKEN))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                ethereumCall(
                                                                contract,
                                                                "createTokenWithAllCustomFeesAvailable",
                                                                spec.registry()
                                                                        .getKey(
                                                                                SECP_256K1_SOURCE_KEY)
                                                                        .getECDSASecp256K1()
                                                                        .toByteArray(),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                feeCollector))),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getTokenID(
                                                                                                EXISTING_TOKEN))),
                                                                asHeadlongAddress(
                                                                        asAddress(
                                                                                spec.registry()
                                                                                        .getAccountID(
                                                                                                RELAYER))),
                                                                8_000_000L)
                                                        .via(firstTxn)
                                                        .gasLimit(GAS_LIMIT)
                                                        .sending(DEFAULT_AMOUNT_TO_SEND)
                                                        .exposingResultTo(
                                                                result -> {
                                                                    log.info(
                                                                            "Explicit create result"
                                                                                    + " is {}",
                                                                            result[0]);
                                                                    final var res =
                                                                            (Address) result[0];
                                                                    createdTokenNum.set(
                                                                            res.value()
                                                                                    .longValueExact());
                                                                }))))
                .then(
                        getTxnRecord(firstTxn).andAllChildRecords().logged(),
                        childRecordsCheck(
                                firstTxn,
                                ResponseCodeEnum.SUCCESS,
                                TransactionRecordAsserts.recordWith()
                                        .status(ResponseCodeEnum.SUCCESS)),
                        withOpContext(
                                (spec, ignore) -> {
                                    final var op = getTxnRecord(firstTxn);
                                    allRunFor(spec, op);

                                    final var callResult =
                                            op.getResponseRecord().getContractCallResult();
                                    final var gasUsed = callResult.getGasUsed();
                                    final var amount = callResult.getAmount();
                                    final var gasLimit = callResult.getGas();
                                    Assertions.assertEquals(DEFAULT_AMOUNT_TO_SEND, amount);
                                    Assertions.assertEquals(GAS_LIMIT, gasLimit);
                                    Assertions.assertTrue(gasUsed > 0L);
                                    Assertions.assertTrue(
                                            callResult.hasContractID() && callResult.hasSenderId());
                                }));
    }

    private HapiApiSpec ETX_SVC_003_contractGetBytecodeQueryReturnsDeployedCode() {
        final var txn = "creation";
        final var contract = "EmptyConstructor";
        return HapiApiSpec.defaultHapiSpec("contractGetBytecodeQueryReturnsDeployedCode")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        uploadInitCode(contract),
                        ethereumContractCreate(contract)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .gasLimit(GAS_LIMIT)
                                .via(txn))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var getBytecode =
                                            getContractBytecode(contract)
                                                    .saveResultTo("contractByteCode");
                                    allRunFor(spec, getBytecode);

                                    final var originalBytecode =
                                            Hex.decode(
                                                    Files.toByteArray(
                                                            new File(
                                                                    getResourcePath(
                                                                            contract, ".bin"))));
                                    final var actualBytecode =
                                            spec.registry().getBytes("contractByteCode");
                                    // The original bytecode is modified on deployment
                                    final var expectedBytecode =
                                            Arrays.copyOfRange(
                                                    originalBytecode, 29, originalBytecode.length);
                                    Assertions.assertArrayEquals(expectedBytecode, actualBytecode);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
