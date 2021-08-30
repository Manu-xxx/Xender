package com.hederahashgraph.fee;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.exception.InvalidTxBodyException;

import java.math.BigInteger;
import java.util.List;

/**
 * This is the base class for building Fee Matrices and calculating the Total as well as specific
 * component Fee for a given Transaction or Query. It includes common methods which is used to
 * calculate Fee for Crypto, File and Smart Contracts Transactions and Query
 */


public class FeeBuilder {
  public static final long MAX_ENTITY_LIFETIME = 100L * 365L * 24L * 60L * 60L;

  public static final int LONG_SIZE = 8;
  public static final int FEE_MATRICES_CONST = 1;
  public static final int INT_SIZE = 4;
  public static final int BOOL_SIZE = 4;
  public static final long SOLIDITY_ADDRESS = 20;
  public static final int KEY_SIZE = 32;
  public static final int TX_HASH_SIZE = 48;
  public static final long RECEIPT_STORAGE_TIME_SEC = 180;
  public static final int THRESHOLD_STORAGE_TIME_SEC = 90000;
  public static final int FEE_DIVISOR_FACTOR = 1000;
  public static final int HRS_DIVISOR = 3600;
  public static final int BASIC_ENTITY_ID_SIZE = (3 * LONG_SIZE);
  public static final long BASIC_RICH_INSTANT_SIZE = (1L * LONG_SIZE) + INT_SIZE;
  public static final int BASIC_ACCOUNT_AMT_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
  public static final int BASIC_TX_ID_SIZE = BASIC_ENTITY_ID_SIZE + LONG_SIZE;
  public static final int EXCHANGE_RATE_SIZE = 2 * INT_SIZE + LONG_SIZE;
  /**
   * Fields included: status, exchangeRate.
   */
  public static final int BASIC_RECEIPT_SIZE = INT_SIZE + 2 * EXCHANGE_RATE_SIZE;
  /**
   * Fields included: transactionID, nodeAccountID, transactionFee, transactionValidDuration, generateRecord
   */
  public static final int BASIC_TX_BODY_SIZE =
      BASIC_ENTITY_ID_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
  public static final int STATE_PROOF_SIZE = 2000;
  public static final int BASE_FILEINFO_SIZE =
      BASIC_ENTITY_ID_SIZE + LONG_SIZE + (LONG_SIZE) + BOOL_SIZE;
  public static final int BASIC_ACCOUNT_SIZE = 8 * LONG_SIZE + BOOL_SIZE;
  /**
   * Fields included: nodeTransactionPrecheckCode, responseType, cost
   */
  public static final long BASIC_QUERY_RES_HEADER = 2L * INT_SIZE + LONG_SIZE;
  public static final long BASIC_QUERY_HEADER = 212L;
  public static final int BASIC_CONTRACT_CREATE_SIZE = BASIC_ENTITY_ID_SIZE + 6 * LONG_SIZE;
  public static final long BASIC_CONTRACT_INFO_SIZE =
      2L * BASIC_ENTITY_ID_SIZE + SOLIDITY_ADDRESS + BASIC_TX_ID_SIZE;
  /**
   * Fields included in size: receipt (basic size), transactionHash, consensusTimestamp, transactionID
   * transactionFee.
   */
  public static final int BASIC_TX_RECORD_SIZE =
      BASIC_RECEIPT_SIZE + TX_HASH_SIZE + LONG_SIZE + BASIC_TX_ID_SIZE + LONG_SIZE;

  /**
   * This method calculates Fee for specific component (Noe/Network/Service) based upon param
   * componentCoefficients and componentMetrics
   *
   * @param componentCoefficients component coefficients
   * @param componentMetrics compnent metrics
   *
   * @return long representation of the fee in tiny cents
   */
  public static long getComponentFeeInTinyCents(FeeComponents componentCoefficients,
      FeeComponents componentMetrics) {

    long bytesUsageFee = componentCoefficients.getBpt() * componentMetrics.getBpt();
    long verificationFee = componentCoefficients.getVpt() * componentMetrics.getVpt();
    long ramStorageFee = componentCoefficients.getRbh() * componentMetrics.getRbh();
    long storageFee = componentCoefficients.getSbh() * componentMetrics.getSbh();
    long evmGasFee = componentCoefficients.getGas() * componentMetrics.getGas();
    long txValueFee = Math.round((float)(componentCoefficients.getTv() * componentMetrics.getTv()) / 1000);
    long bytesResponseFee = componentCoefficients.getBpr() * componentMetrics.getBpr();
    long storageBytesResponseFee = componentCoefficients.getSbpr() * componentMetrics.getSbpr();
    long componentUsage = componentCoefficients.getConstant() * componentMetrics.getConstant();

    long totalComponentFee = componentUsage + (bytesUsageFee + verificationFee + ramStorageFee
        + storageFee + evmGasFee + txValueFee + bytesResponseFee + storageBytesResponseFee);

    if (totalComponentFee < componentCoefficients.getMin()) {
      totalComponentFee = componentCoefficients.getMin();
    } else if (totalComponentFee > componentCoefficients.getMax()) {
      totalComponentFee = componentCoefficients.getMax();
    }
    return Math.max(totalComponentFee > 0 ? 1 : 0, (totalComponentFee) / FEE_DIVISOR_FACTOR);
  }

  /**
   * This method calculates total fee for transaction or query and returns the value in tinyBars
   *
   * @param feeCoefficients fee coefficients
   * @param componentMetrics component metrics
   * @param exchangeRate exchange rates
   *
   * @return long representing the total fee request
   */
  public static long getTotalFeeforRequest(FeeData feeCoefficients, FeeData componentMetrics,
      ExchangeRate exchangeRate) {

    FeeObject feeObject = getFeeObject(feeCoefficients, componentMetrics,exchangeRate);
    return feeObject.getServiceFee() + feeObject.getNodeFee() + feeObject.getNetworkFee();
  }

  public static FeeObject getFeeObject(
          FeeData feeData,
          FeeData feeMatrices,
          ExchangeRate exchangeRate,
          long multiplier
  ) {
    // get the Network Fee
    long networkFee = getComponentFeeInTinyCents(feeData.getNetworkdata(), feeMatrices.getNetworkdata());
    long nodeFee = getComponentFeeInTinyCents(feeData.getNodedata(), feeMatrices.getNodedata());
    long serviceFee = getComponentFeeInTinyCents(feeData.getServicedata(), feeMatrices.getServicedata());
    // convert the Fee to tiny hbars
    networkFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, networkFee) * multiplier;
    nodeFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, nodeFee) * multiplier;
    serviceFee = FeeBuilder.getTinybarsFromTinyCents(exchangeRate, serviceFee) * multiplier;
    return new FeeObject(nodeFee, networkFee, serviceFee);
  }

  /**
   * Get fee object
   *
   * @param feeData fee data
   * @param feeMatrices fee matrices
   * @param exchangeRate exchange rate
   *
   * @return fee object
   */
  public static FeeObject getFeeObject(FeeData feeData, FeeData feeMatrices, ExchangeRate exchangeRate) {
  	return getFeeObject(feeData, feeMatrices, exchangeRate, 1L);
  }

  /**
   * This method calculates the common bytes included in a every transaction. Common bytes only
   * differ based upon memo field.
   * <p>
   * Common fields in all transaction:
   * </p>
   * <ul>
   *     <li>TransactionID transactionID - BASIC_ENTITY_ID_SIZE (accountId) + LONG_SIZE (transactionValidStart)</li>
   *     <li>AccountID nodeAccountID - BASIC_ENTITY_ID_SIZE</li>
   *     <li>uint64 transactionFee - LONG_SIZE</li>
   *     <li>Duration transactionValidDuration - (LONG_SIZE)</li>
   *     <li>bool generateRecord - BOOL_SIZE</li>
   *     <li>bytes string memo - get memo size from transaction</li>
   * </ul>
   *
   * @param txBody transaction body
   * @return long representing transaction size
   * @throws InvalidTxBodyException when transaction body is invalid
   */
  public static long getCommonTransactionBodyBytes(TransactionBody txBody) throws InvalidTxBodyException {
    if (txBody == null) {
      throw new InvalidTxBodyException("Transaction Body not available for Fee Calculation");
    }
    int memoSize = 0;
    if (txBody.getMemo() != null) {
      memoSize = txBody.getMemoBytes().size();
    }
    return (long) BASIC_TX_BODY_SIZE + memoSize;
  }

  /**
   * This method returns the Key size in bytes
   *
   * @param key key
   * @return int representing account key storage size
   */
  public static int getAccountKeyStorageSize(Key key) {

    if (key == null) {
      return 0;
    }
    if (key == Key.getDefaultInstance()) {
      return 0;
    }

    int[] countKeyMetatData = {0, 0};
    countKeyMetatData = calculateKeysMetadata(key, countKeyMetatData);

    return countKeyMetatData[0] * KEY_SIZE + countKeyMetatData[1] * INT_SIZE;
  }

  /**
   * This method calculates number of keys
   *
   * @param key key
   * @param count count array
   *
   * @return int array containing key metadata
   */
  public static int[] calculateKeysMetadata(Key key, int[] count) {
    if (key.hasKeyList()) {
      List<Key> keyList = key.getKeyList().getKeysList();
      for (Key value : keyList) {
        count = calculateKeysMetadata(value, count);
      }
    } else if (key.hasThresholdKey()) {
      List<Key> keyList = key.getThresholdKey().getKeys().getKeysList();
      count[1]++;
      for (Key value : keyList) {
        count = calculateKeysMetadata(value, count);
      }
    } else {
      count[0]++;
    }
    return count;
  }

  /**
   * This method returns the fee matrices for querying based upon ID (account / file / smart
   * contract)
   *
   * @return fee data
   */
  public static FeeData getCostForQueryByIDOnly() {
    return FeeData.getDefaultInstance();
  }

  /**
   * Get signature count
   *
   * @param transaction transaction
   *
   * @return int representing signature count
   */
  public static int getSignatureCount(Transaction transaction) {
    try {
      return CommonUtils.extractSignatureMap(transaction).getSigPairCount();
    } catch (InvalidProtocolBufferException ignored) { }
    return 0;
  }

  /**
   * Get signature size
   *
   * @param transaction transaction
   *
   * @return int representing signature size
   */
  public static int getSignatureSize(Transaction transaction) {
    try {
      return CommonUtils.extractSignatureMap(transaction).toByteArray().length;
    } catch (InvalidProtocolBufferException ignored) { }
    return 0;
  }

  /**
   * Convert tinyCents to tinybars
   *
   * @param exchangeRate exchange rate
   * @param tinyCentsFee tiny cents fee
   *
   * @return tinyHbars
   */
  public static long getTinybarsFromTinyCents(ExchangeRate exchangeRate, long tinyCentsFee) {
    BigInteger hbarMultiplier = BigInteger.valueOf(exchangeRate.getHbarEquiv());
    BigInteger centsDivisor = BigInteger.valueOf(exchangeRate.getCentEquiv());
    BigInteger feeInBigInt = BigInteger.valueOf(tinyCentsFee);
    feeInBigInt = feeInBigInt.multiply(hbarMultiplier);
    feeInBigInt = feeInBigInt.divide(centsDivisor);
    return feeInBigInt.longValue();
  }


  public static FeeData getFeeDataMatrices(FeeComponents feeComponents, int payerVpt, long rbsNetwork) {

    long rbh = Math.max(feeComponents.getRbh() > 0 ? 1 : 0, feeComponents.getRbh() / HRS_DIVISOR);
    long sbh = Math.max(feeComponents.getSbh() > 0 ? 1 : 0, feeComponents.getSbh() / HRS_DIVISOR);
    long rbhNetwork = Math.max(rbsNetwork > 0 ? 1 : 0, (rbsNetwork) / HRS_DIVISOR);
    FeeComponents feeMatricesForTxService =
        FeeComponents.newBuilder().setConstant(FEE_MATRICES_CONST)
            .setRbh(rbh).setSbh(sbh)
            .setTv(feeComponents.getTv()).build();

    FeeComponents feeMatricesForTxNetwork =
        FeeComponents.newBuilder().setConstant(FEE_MATRICES_CONST)
            .setBpt(feeComponents.getBpt()).setVpt(feeComponents.getVpt())
            .setRbh(rbhNetwork).build();

    FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
        .setConstant(FEE_MATRICES_CONST)
        .setBpt(feeComponents.getBpt())
        .setVpt(payerVpt)
        .setBpr(feeComponents.getBpr()).setSbpr(feeComponents.getSbpr()).build();

    return FeeData.newBuilder().setNetworkdata(feeMatricesForTxNetwork)
        .setNodedata(feeMatricesForTxNode).setServicedata(feeMatricesForTxService).build();
  }


  public static FeeData getQueryFeeDataMatrices(FeeComponents feeComponents) {

    FeeComponents feeMatricesForTxService = FeeComponents.getDefaultInstance();

    FeeComponents feeMatricesForTxNetwork = FeeComponents.getDefaultInstance();

    FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
        .setConstant(FEE_MATRICES_CONST)
        .setBpt(feeComponents.getBpt())
        .setBpr(feeComponents.getBpr())
        .setSbpr(feeComponents.getSbpr())
        .build();

    return FeeData.newBuilder().setNetworkdata(feeMatricesForTxNetwork)
        .setNodedata(feeMatricesForTxNode).setServicedata(feeMatricesForTxService).build();
  }

  public static long getDefaultRBHNetworkSize() {
    return (BASIC_RECEIPT_SIZE) * (RECEIPT_STORAGE_TIME_SEC);
  }

  public static int getBaseTransactionRecordSize(TransactionBody txBody) {
    int txRecordSize = BASIC_TX_RECORD_SIZE;
    if (txBody.getMemo() != null) {
      txRecordSize = txRecordSize + txBody.getMemoBytes().size();
    }
    // TransferList size
    if (txBody.hasCryptoTransfer()) {
      txRecordSize = txRecordSize
          + txBody.getCryptoTransfer().getTransfers().getAccountAmountsCount()
          * (BASIC_ACCOUNT_AMT_SIZE);
    }
    return txRecordSize;
  }

  public static long getTxRecordUsageRBH(TransactionRecord txRecord, int timeInSeconds) {
	if(txRecord == null) {
      return 0;
    }
	long txRecordSize = getTransactionRecordSize(txRecord);
    return (txRecordSize) * getHoursFromSec(timeInSeconds);
  }

  public static int getHoursFromSec(int valueInSeconds) {
	  return valueInSeconds==0 ? 0 : Math.max(1,(valueInSeconds/HRS_DIVISOR));
  }

  public static int getTransactionRecordSize(TransactionRecord txRecord) {

	if(txRecord == null) {
      return 0;
    }

    int txRecordSize = BASIC_TX_RECORD_SIZE;

    if (txRecord.hasContractCallResult()) {
      txRecordSize =
          txRecordSize + getContractFunctionSize(txRecord.getContractCallResult());
    } else if (txRecord.hasContractCreateResult()) {
      txRecordSize =
          txRecordSize + getContractFunctionSize(txRecord.getContractCreateResult());
    }
    if (txRecord.hasTransferList()) {
      txRecordSize =
          txRecordSize
              + (txRecord.getTransferList().getAccountAmountsCount()) * (BASIC_ACCOUNT_AMT_SIZE);
    }

    int memoBytesSize = 0;
    if (txRecord.getMemo() != null) {
      memoBytesSize = txRecord.getMemoBytes().size();
    }

    return txRecordSize + memoBytesSize;

  }

  public static int getContractFunctionSize(ContractFunctionResult contFuncResult) {

    int contResult = 0;

    if (contFuncResult.getContractCallResult() != null) {
      contResult = contFuncResult.getContractCallResult().size();
    }

    if (contFuncResult.getErrorMessage() != null) {
      contResult = contResult + contFuncResult.getErrorMessageBytes().size();
    }

    if (contFuncResult.getBloom() != null) {
      contResult = contResult + contFuncResult.getBloom().size();
    }
    contResult = contResult + LONG_SIZE + 2 * LONG_SIZE;

    return contResult;
  }

  public static int getStateProofSize(ResponseType responseType) {
    return (responseType == ResponseType.ANSWER_STATE_PROOF
        || responseType == ResponseType.COST_ANSWER_STATE_PROOF) ? STATE_PROOF_SIZE : 0;
  }

  protected long calculateRBS(TransactionBody txBody) {
    return getBaseTransactionRecordSize(txBody) * RECEIPT_STORAGE_TIME_SEC;
  }

  protected long calculateBPT(){
    return BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE;
  }

}
