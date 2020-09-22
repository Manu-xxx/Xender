package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * Calling and deleting nonexistent smart contracts (account, never existed)
 *
 * @author Peter
 */
public class SmartContractNonexistent {
	 private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	  private final Logger log = LogManager.getLogger(SmartContractPay.class);


	  private static final int MAX_RECEIPT_RETRIES = 60;
	  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	  private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
	  private static final String SC_SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	  private static final long MAX_TX_FEE = TestHelper.getContractMaxFee();
	  private static AccountID nodeAccount;
	  private static long node_account_number;
	  private static long node_shard_number;
	  private static long node_realm_number;
	  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	  private AccountID genesisAccount;
	  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	  private KeyPair adminKeyPair = null;
	  private static String host;
	  private static int port;

	  public static void main(String args[]) throws Exception {
	    Properties properties = TestHelper.getApplicationProperties();
	    host = properties.getProperty("host");
	    port = Integer.parseInt(properties.getProperty("port"));
	    node_account_number = Utilities.getDefaultNodeAccount();
	    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
	    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
	    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
	        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

	    int numberOfReps = 1;
	    for (int i = 0; i < numberOfReps; i++) {
	    	SmartContractNonexistent scSs = new SmartContractNonexistent();
	      scSs.demo();
	    }

	  }


	  private void loadGenesisAndNodeAcccounts() throws Exception {
	    Map<String, List<AccountKeyListObj>> hederaAccounts = null;
	    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

	    // Get Genesis Account key Pair
	    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
	    ;

	    // get Private Key
			KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
			PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
			KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

	    // get the Account Object
	    genesisAccount = genesisAccountList.get(0).getAccountId();
			accountKeyPairs.put(genesisAccount, genesisKeyPair);
	  }

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
				nodeAccount, payer,
				accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;
	}

	  private AccountID createAccount(AccountID payerAccount, long initialBalance) throws Exception {

	    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
	    return createAccount(keyGenerated, payerAccount, initialBalance);
	  }

	  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
	      throws Exception {
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    Transaction transaction = TestHelper
	        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
	            accountKeyPairs.get(payerAccount));
	    TransactionResponse response = stub.createAccount(transaction);
	    Assert.assertNotNull(response);
	    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	    System.out.println(
	        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
	            .name());

	    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
	    AccountID newlyCreateAccountId = TestHelper
	        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
			accountKeyPairs.put(newlyCreateAccountId, keyPair);
	    channel.shutdown();
	    return newlyCreateAccountId;
	  }

	  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
	    TransactionGetReceiptResponse receiptToReturn = null;
	    Query query = Query.newBuilder()
	        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
	            transactionId, ResponseType.ANSWER_ONLY)).build();
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    Response transactionReceipts = stub.getTransactionReceipts(query);
	    int attempts = 1;
	    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
	        .getReceipt()
	        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.UNKNOWN.name())) {
	      Thread.sleep(1000);
	      transactionReceipts = stub.getTransactionReceipts(query);
	      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
	          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
	      attempts++;
	    }
	    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
	        .equals(ResponseCodeEnum.SUCCESS)) {
	      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
	    }
	    channel.shutdown();
	    return transactionReceipts.getTransactionGetReceipt();

	  }

	  private ContractID createContract(AccountID payerAccount, FileID contractFile,
	      long durationInSeconds) throws Exception {
	    ContractID createdContract = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();

	    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    Transaction createContractRequest = TestHelper
	        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
	            nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
	            transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
	            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

	    TransactionResponse response = stub.createContract(createContractRequest);
	    System.out.println(
	        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());

	    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
	    		createContractBody.getTransactionID());
	    if (contractCreateReceipt != null) {
	      createdContract = contractCreateReceipt.getReceipt().getContractID();
	    }
	    channel.shutdown();

	    return createdContract;
	  }


	  private byte[] encodeSet(int valueToAdd) {
	    String retVal = "";
	    CallTransaction.Function function = getSetFunction();
	    byte[] encodedFunc = function.encode(valueToAdd);

	    return encodedFunc;
	  }

	  private CallTransaction.Function getSetFunction() {
	    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
	      throws Exception {
	    byte[] dataToReturn = null;
	    ContractID createdContract = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
	    ByteString dataBstr = ByteString.EMPTY;
	    if (data != null) {
	      dataBstr = ByteString.copyFrom(data);
	    }
	    Transaction callContractRequest = TestHelper
	        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), node_account_number, 0l, 0l, MAX_TX_FEE, timestamp,
	            transactionDuration, 250000, contractToCall, dataBstr, 0,
	            accountKeyPairs.get(payerAccount));

	    TransactionResponse response = stub.contractCallMethod(callContractRequest);
	    System.out.println(
	        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());
	    Thread.sleep(1000);
	    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
	    		callContractBody.getTransactionID());
	    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
	        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
	      TransactionRecord trRecord = getTransactionRecord(payerAccount,
	    		  callContractBody.getTransactionID());
	      if (trRecord != null && trRecord.hasContractCallResult()) {
	        ContractFunctionResult callResults = trRecord.getContractCallResult();
	        log.info("Gas used : " + callResults.getGasUsed());
	        String errMsg = callResults.getErrorMessage();
	        if (StringUtils.isEmpty(errMsg)) {
	          if (!callResults.getContractCallResult().isEmpty()) {
	            dataToReturn = callResults.getContractCallResult().toByteArray();
	          }
	        } else {
	          log.info("@@@ Contract Call resulted in error: " + errMsg);
	        }
	      }
	    }
	    channel.shutdown();

	    return dataToReturn;
	  }


	  private TransactionRecord getTransactionRecord(AccountID payerAccount,
	      TransactionID transactionId) throws Exception {
	    AccountID createdAccount = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
	    long fee = FeeClient.getCostForGettingTxRecord();
	    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
	        ResponseType.COST_ANSWER);
	    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
	    recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
	        ResponseType.ANSWER_ONLY);
	    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
	    System.out.println("tx record = " + txRecord);
	    channel.shutdown();
	    return txRecord;
	  }

	  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
	      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
	      throws Exception {
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query getRecordQuery = RequestBuilder
	        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
	    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
	    return recordResp;
	  }

	private ResponseCodeEnum callContract(AccountID payerAccount, ContractID contractToCall,
			byte[] data, long value) throws Exception {
	    ContractID createdContract = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
	    ByteString dataBstr = ByteString.EMPTY;
	    if (data != null) {
	      dataBstr = ByteString.copyFrom(data);
	    }
	    Transaction callContractRequest = TestHelper
	        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
	            payerAccount.getShardNum(), node_account_number, 0l, 0l, MAX_TX_FEE, timestamp,
	            transactionDuration, 25000, contractToCall, dataBstr, value,
	            accountKeyPairs.get(payerAccount));

	    TransactionResponse response = stub.contractCallMethod(callContractRequest);
	    System.out.println(
	        " callContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());
	    if (response.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK) {
	    	return response.getNodeTransactionPrecheckCode();
			}
	    Thread.sleep(1000);
	    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
	    		callContractBody.getTransactionID());
			System.out.println(
          " callContract receipt status :: " + contractCallReceipt.getReceipt().getStatus()
              .name());
	    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
	        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
	      TransactionRecord trRecord = getTransactionRecord(payerAccount,
	    		  callContractBody.getTransactionID());
	      if (trRecord != null && trRecord.hasContractCallResult()) {
	        ContractFunctionResult callResults = trRecord.getContractCallResult();
	        log.info("Gas used : " + callResults.getGasUsed());
	        String errMsg = callResults.getErrorMessage();
	        if (! StringUtils.isEmpty(errMsg)) {
	          log.info("@@@ Contract Call resulted in error: " + errMsg);
	        }
	      }
	    }
	    channel.shutdown();

	    return contractCallReceipt.getReceipt().getStatus();
	  }

	  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data,
				ResponseCodeEnum expectedPrecheckCode)
	      throws Exception {
	    byte[] dataToReturn = null;
	    AccountID createdAccount = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    ByteString callData = ByteString.EMPTY;
	    int callDataSize = 0;
	    if (data != null) {
	      callData = ByteString.copyFrom(data);
	      callDataSize = callData.size();
	    }
	    // Fake the fee because the COST_ANSWER call will return 2 for INVALID_CONTRACT_IDs
	    long fee = FeeClient.getCostContractCallLocalFee(callDataSize) * 5;
	    Response callResp;
	    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
	        ResponseType.ANSWER_ONLY);

	    System.out.println("callContractLocal response = " + callResp);
      Assert.assertEquals(expectedPrecheckCode,
          callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());

	    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
	        .getContractCallResult();

	    channel.shutdown();
	    return functionResults.toByteArray();
	  }


	  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
	      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
	      ResponseType resposeType)
	      throws Exception {
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query contractCallLocal = RequestBuilder
	        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
	            resposeType);

	    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
	    return callResp;
	  }



	  private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
	      throws Exception {
	    byte[] dataToSet = encodeSet(valuetoSet);
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet);
	  }

	  public ResponseCodeEnum updateContract(AccountID payerAccount, ContractID contractToUpdate,
	      Duration autoRenewPeriod) throws Exception {

	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
	    Duration transactionDuration = RequestBuilder.getDuration(30);
	    Transaction updateContractRequest = RequestBuilder
	        .getContractUpdateRequest(payerAccount, nodeAccount, MAX_TX_FEE, timestamp,
	            transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
	            null, "");

			List<Key> keyList = new ArrayList<>();
			HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
			KeyPair payerKeyPair = accountKeyPairs.get(payerAccount);
			keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
			Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
			keyList.add(Common.PrivateKeyToKey(adminKeyPair.getPrivate()));
			Common.addKeyMap(adminKeyPair, pubKey2privKeyMap);

			updateContractRequest = TransactionSigner
					.signTransactionComplexWithSigMap(updateContractRequest, keyList, pubKey2privKeyMap);
	    TransactionResponse response = stub.updateContract(updateContractRequest);
	    System.out.println(
	        " update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
	            .name());
	    if (response.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK) {
	    	return response.getNodeTransactionPrecheckCode();
			}
	    Thread.sleep(1000);
	    TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
	    TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(
	    		updateContractBody.getTransactionID());
	    Assert.assertNotNull(contractUpdateReceipt);
	    channel.shutdown();

	    return contractUpdateReceipt.getReceipt().getStatus();
	  }

	  private String getContractByteCode(AccountID payerAccount,
	      ContractID contractId) throws Exception {
	    String byteCode = "";
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetBytecode);
	    Response respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
	        ResponseType.COST_ANSWER);

	    fee = respToReturn.getContractGetBytecodeResponse().getHeader().getCost();
	    respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
	        ResponseType.ANSWER_ONLY);
	    ByteString contractByteCode = null;
	    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
	    if (contractByteCode != null && !contractByteCode.isEmpty()) {
	      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
	    }
	    channel.shutdown();

	    return byteCode;
	  }


	  private Response executeContractByteCodeQuery(AccountID payerAccount, ContractID contractId,
	      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
	      ResponseType responseType) throws Exception {
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query getContractBytecodeQuery = RequestBuilder
	        .getContractGetBytecodeQuery(contractId, paymentTx, responseType);
	    Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
	    return respToReturn;
	  }


	  private AccountInfo getCryptoGetAccountInfo(AccountID payerAccount,
	      AccountID accountID) throws Exception {
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

	    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetInfo);

	    Response respToReturn = executeGetAcctInfoQuery(payerAccount, accountID, stub, fee,
	        ResponseType.COST_ANSWER);

	    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
	    respToReturn = executeGetAcctInfoQuery(payerAccount, accountID, stub, fee,
	        ResponseType.ANSWER_ONLY);
	    AccountInfo accInfToReturn = null;
	    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
	    channel.shutdown();

	    return accInfToReturn;
	  }


	  private Response executeGetAcctInfoQuery(AccountID payerAccount, AccountID accountID,
	      CryptoServiceGrpc.CryptoServiceBlockingStub stub,
	      long fee, ResponseType responseType) throws Exception {
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query cryptoGetInfoQuery = RequestBuilder
	        .getCryptoGetInfoQuery(accountID, paymentTx, responseType);

	    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
	    return respToReturn;
	  }

	  private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
	      String solidityId) throws Exception {
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    long fee = FeeClient.getFeegetBySolidityID();
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query getBySolidityIdQuery = RequestBuilder.getBySolidityIDQuery(
	        solidityId, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
	    GetBySolidityIDResponse bySolidityReturn = null;
	    bySolidityReturn = respToReturn.getGetBySolidityID();
	    channel.shutdown();

	    return bySolidityReturn;
	  }


	  public void demo() throws Exception {
	    loadGenesisAndNodeAcccounts();

			ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
					.usePlaintext(true)
					.build();
			TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
					nodeAccount);
			channel.shutdown();

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
	    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 20);
	    KeyPair obtainerKeyPair = new KeyPairGenerator().generateKeyPair();
	    AccountID obtainerAcc = createAccount(obtainerKeyPair, genesisAccount, 100000);
	    adminKeyPair = new KeyPairGenerator().generateKeyPair();
	    log.info("Accounts created successfully");

	    // Select a number far in the future that won't exist yet
			ContractID nonexistentContractId = ContractID.newBuilder().
					setContractNum(crAccount.getAccountNum() + 5000).build();

			// Fail local call
			getBalanceFromContract(crAccount, nonexistentContractId, ResponseCodeEnum.INVALID_CONTRACT_ID);
			log.info("Nonexistent contract could not be called locally");

			// Fail regular call
			ResponseCodeEnum callMissingContract =
					depositToContract(crAccount, nonexistentContractId, 10);
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,callMissingContract);
			log.info("Nonexistent contract could not be called");

			Duration changeDuration = RequestBuilder.getDuration(DAY_SEC);
			ResponseCodeEnum updateMissingContract =
					updateContract(crAccount, nonexistentContractId, changeDuration);
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,updateMissingContract);
			log.info("Nonexistent contract could not be updated");

			// Fail to delete
			ResponseCodeEnum deleteMissingContract = deleteContract(crAccount, nonexistentContractId, obtainerAcc,
					null);
			// This will only work when DeleteContract is added to the fee schedule.
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,deleteMissingContract);
			log.info("Nonexistent contract could not be deleted");

			ContractID badContractId = ContractID.newBuilder().setContractNum(crAccount.getAccountNum())
					.build();

			// Fail local call
			getBalanceFromContract(crAccount, badContractId, ResponseCodeEnum.INVALID_CONTRACT_ID);
			log.info("Contract ID with account seq could not be called locally");

			// Fail regular call
			callMissingContract =
					depositToContract(crAccount, badContractId, 10);
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,callMissingContract);
			log.info("Contract ID with account seq could not be called");

			changeDuration = RequestBuilder.getDuration(DAY_SEC);
			updateMissingContract =
					updateContract(crAccount, badContractId, changeDuration);
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,updateMissingContract);
			log.info("Contract ID with account seq could not be updated");

			// Fail to delete
			deleteMissingContract = deleteContract(crAccount, badContractId, obtainerAcc,
					null);
			// This will only work when DeleteContract is added to the fee schedule.
			Assert.assertEquals(ResponseCodeEnum.INVALID_CONTRACT_ID,deleteMissingContract);
			log.info("Contract ID with account seq could not be deleted");
	  }


	  private ContractInfo getContractInfo(AccountID payerAccount,
		      ContractID contractId,ResponseCodeEnum expectedConstResponse , ResponseCodeEnum expecedAnswerResponse) throws Exception {
		    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
		        .usePlaintext(true)
		        .build();
		    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
		        .newBlockingStub(channel);
		    ContractInfo contractInfToReturn = null;
		    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetInfo);

		    Response respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
		        ResponseType.COST_ANSWER);
        Assert.assertEquals(expectedConstResponse,
            respToReturn.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());

		    if(respToReturn.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode()==ResponseCodeEnum.OK) {
			    fee = respToReturn.getContractGetInfo().getHeader().getCost();
			    respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
			        ResponseType.ANSWER_ONLY);

          Assert.assertEquals(expecedAnswerResponse,
              respToReturn.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
			    contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
		    }
		    channel.shutdown();

		    return contractInfToReturn;
		  }

	  private Response executeGetContractInfo(AccountID payerAccount, ContractID contractId,
	      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
	      ResponseType responseType) throws Exception {
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);

	    Query getContractInfoQuery = RequestBuilder
	        .getContractGetInfoQuery(contractId, paymentTx, responseType);

	    Response respToReturn = stub.getContractInfo(getContractInfoQuery);
	    return respToReturn;
	  }


	  private byte[] encodeDeposit(int valueToDeposit) {
	    String retVal = "";
	    CallTransaction.Function function = getDepositFunction();
	    byte[] encodedFunc = function.encode(valueToDeposit);

	    return encodedFunc;
	  }

	  private CallTransaction.Function getDepositFunction() {
	    String funcJson = SC_DEPOSIT.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }


	  private CallTransaction.Function getGetBalanceFunction() {
	    String funcJson = SC_GET_BALANCE.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeGetBalance() {
	    String retVal = "";
	    CallTransaction.Function function = getGetBalanceFunction();
	    byte[] encodedFunc = function.encode();
	    return encodedFunc;
	  }

	  private int decodeGetBalanceResult(byte[] value) {
	    int decodedReturnedValue = 0;
	    CallTransaction.Function function = getGetBalanceFunction();
	    Object[] retResults = function.decodeResult(value);
	    if (retResults != null && retResults.length > 0) {
	      BigInteger retBi = (BigInteger) retResults[0];
	      decodedReturnedValue = retBi.intValue();
	    }
	    return decodedReturnedValue;
	  }


	private ResponseCodeEnum depositToContract(AccountID payerAccount, ContractID contractId,
			int valueToDeposit) throws Exception {
	    byte[] dataToSet = encodeDeposit(valueToDeposit);
	    return callContract(payerAccount, contractId, dataToSet, valueToDeposit);
	  }


	  private int getBalanceFromContract(AccountID payerAccount, ContractID contractId,
				ResponseCodeEnum expectedPrecheckCode)
	      throws Exception {
	    int retVal = 0;
	    byte[] getBalanceEncodedFunction = encodeGetBalance();
	    byte[] result = callContractLocal(payerAccount, contractId, getBalanceEncodedFunction,
          expectedPrecheckCode);
	    if (result != null && result.length > 0) {
	      retVal = decodeGetBalanceResult(result);
	    }
	    return retVal;
	  }

	  private CallTransaction.Function getSendFundsFunction() {
	    String funcJson = SC_SEND_FUNDS.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeSendFunds(String address, int amount) {
	    String retVal = "";
	    CallTransaction.Function function = getSendFundsFunction();
	    byte[] encodedFunc = function.encode(address, amount);
	    return encodedFunc;
	  }

	  private void sendFunds(AccountID payerAccount, ContractID contractId, String receiverAccount,
	      int valueToSend) throws Exception {
	    byte[] dataToSet = encodeSendFunds(receiverAccount, valueToSend);
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet);
	  }


	  private ResponseCodeEnum deleteContract(AccountID payerAccount, ContractID contractId,
		      AccountID transferAccount , ContractID transferContractID ) throws Exception {
		    ContractID createdContract = null;
		    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
		        .usePlaintext(true)
		        .build();

		   
		   SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
		        .newBlockingStub(channel);

		    Timestamp timestamp = RequestBuilder
		        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		    Duration transactionDuration = RequestBuilder.getDuration(30);

				List<KeyPair> keyPairList = new ArrayList<>();
				keyPairList.add(adminKeyPair);
				keyPairList.add(accountKeyPairs.get(payerAccount));
	        
		    Transaction deleteContractRequest =  TestHelper.getDeleteContractRequestSigMap(
		    		payerAccount, nodeAccount, MAX_TX_FEE, timestamp, transactionDuration,
						true, "", contractId, transferAccount, transferContractID, keyPairList);

		    TransactionResponse response = stub.deleteContract(deleteContractRequest);
		    System.out.println(
		        " deleteContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
		            .name());
			if (response.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK) {
				return response.getNodeTransactionPrecheckCode();
			}

		    TransactionBody deleteContractBody = TransactionBody.parseFrom(deleteContractRequest.getBodyBytes());
		    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
		    		deleteContractBody.getTransactionID());
		    
		    channel.shutdown();

		    return contractDeleteReceipt.getReceipt().getStatus();
		  }
}
