/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Record representing the result of a key lookup for signature requirements. If the key lookup
 * succeeds, failureReason will be null. Else if, key lookup failed failureReason will be set
 * providing information about the failure and the key will be set to null. In some cases, when the
 * key need not be looked up when receiver signature required is false, both key and failureReason
 * are null.
 */
public record KeyOrLookupFailureReason(@Nullable HederaKey key, @Nullable ResponseCodeEnum failureReason) {
    public static final KeyOrLookupFailureReason PRESENT_BUT_NOT_REQUIRED = new KeyOrLookupFailureReason(null, null);

    private static KeyOrLookupFailureReason withFailureReason(final com.hederahashgraph.api.proto.java.ResponseCodeEnum accountIdDoesNotExist) {
        return new KeyOrLookupFailureReason(null, convertToPbj(accountIdDoesNotExist));
    }

    // this duplicates PbjConverter, but that class would require a circular dependency, so we have this here.
    @SuppressWarnings("deprecation")
    private static ResponseCodeEnum convertToPbj(final com.hederahashgraph.api.proto.java.ResponseCodeEnum accountIdDoesNotExist) {
        return switch(accountIdDoesNotExist) {
            case OK -> ResponseCodeEnum.OK;
            case INVALID_TRANSACTION -> ResponseCodeEnum.INVALID_TRANSACTION;
            case PAYER_ACCOUNT_NOT_FOUND -> ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
            case INVALID_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_NODE_ACCOUNT;
            case TRANSACTION_EXPIRED -> ResponseCodeEnum.TRANSACTION_EXPIRED;
            case INVALID_TRANSACTION_START -> ResponseCodeEnum.INVALID_TRANSACTION_START;
            case INVALID_TRANSACTION_DURATION -> ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
            case INVALID_SIGNATURE -> ResponseCodeEnum.INVALID_SIGNATURE;
            case MEMO_TOO_LONG -> ResponseCodeEnum.MEMO_TOO_LONG;
            case INSUFFICIENT_TX_FEE -> ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            case INSUFFICIENT_PAYER_BALANCE -> ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
            case DUPLICATE_TRANSACTION -> ResponseCodeEnum.DUPLICATE_TRANSACTION;
            case BUSY -> ResponseCodeEnum.BUSY;
            case NOT_SUPPORTED -> ResponseCodeEnum.NOT_SUPPORTED;
            case INVALID_FILE_ID -> ResponseCodeEnum.INVALID_FILE_ID;
            case INVALID_ACCOUNT_ID -> ResponseCodeEnum.INVALID_ACCOUNT_ID;
            case INVALID_CONTRACT_ID -> ResponseCodeEnum.INVALID_CONTRACT_ID;
            case INVALID_TRANSACTION_ID -> ResponseCodeEnum.INVALID_TRANSACTION_ID;
            case RECEIPT_NOT_FOUND -> ResponseCodeEnum.RECEIPT_NOT_FOUND;
            case RECORD_NOT_FOUND -> ResponseCodeEnum.RECORD_NOT_FOUND;
            case INVALID_SOLIDITY_ID -> ResponseCodeEnum.INVALID_SOLIDITY_ID;
            case UNKNOWN -> ResponseCodeEnum.UNKNOWN;
            case SUCCESS -> ResponseCodeEnum.SUCCESS;
            case FAIL_INVALID -> ResponseCodeEnum.FAIL_INVALID;
            case FAIL_FEE -> ResponseCodeEnum.FAIL_FEE;
            case FAIL_BALANCE -> ResponseCodeEnum.FAIL_BALANCE;
            case KEY_REQUIRED -> ResponseCodeEnum.KEY_REQUIRED;
            case BAD_ENCODING -> ResponseCodeEnum.BAD_ENCODING;
            case INSUFFICIENT_ACCOUNT_BALANCE -> ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
            case INVALID_SOLIDITY_ADDRESS -> ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
            case INSUFFICIENT_GAS -> ResponseCodeEnum.INSUFFICIENT_GAS;
            case CONTRACT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.CONTRACT_SIZE_LIMIT_EXCEEDED;
            case LOCAL_CALL_MODIFICATION_EXCEPTION -> ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
            case CONTRACT_REVERT_EXECUTED -> ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
            case CONTRACT_EXECUTION_EXCEPTION -> ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
            case INVALID_RECEIVING_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
            case MISSING_QUERY_HEADER -> ResponseCodeEnum.MISSING_QUERY_HEADER;
            case ACCOUNT_UPDATE_FAILED -> ResponseCodeEnum.ACCOUNT_UPDATE_FAILED;
            case INVALID_KEY_ENCODING -> ResponseCodeEnum.INVALID_KEY_ENCODING;
            case NULL_SOLIDITY_ADDRESS -> ResponseCodeEnum.NULL_SOLIDITY_ADDRESS;
            case CONTRACT_UPDATE_FAILED -> ResponseCodeEnum.CONTRACT_UPDATE_FAILED;
            case INVALID_QUERY_HEADER -> ResponseCodeEnum.INVALID_QUERY_HEADER;
            case INVALID_FEE_SUBMITTED -> ResponseCodeEnum.INVALID_FEE_SUBMITTED;
            case INVALID_PAYER_SIGNATURE -> ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
            case KEY_NOT_PROVIDED -> ResponseCodeEnum.KEY_NOT_PROVIDED;
            case INVALID_EXPIRATION_TIME -> ResponseCodeEnum.INVALID_EXPIRATION_TIME;
            case NO_WACL_KEY -> ResponseCodeEnum.NO_WACL_KEY;
            case FILE_CONTENT_EMPTY -> ResponseCodeEnum.FILE_CONTENT_EMPTY;
            case INVALID_ACCOUNT_AMOUNTS -> ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
            case EMPTY_TRANSACTION_BODY -> ResponseCodeEnum.EMPTY_TRANSACTION_BODY;
            case INVALID_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_TRANSACTION_BODY;
            case INVALID_SIGNATURE_TYPE_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
            case INVALID_SIGNATURE_COUNT_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
            case EMPTY_LIVE_HASH_BODY -> ResponseCodeEnum.EMPTY_LIVE_HASH_BODY;
            case EMPTY_LIVE_HASH -> ResponseCodeEnum.EMPTY_LIVE_HASH;
            case EMPTY_LIVE_HASH_KEYS -> ResponseCodeEnum.EMPTY_LIVE_HASH_KEYS;
            case INVALID_LIVE_HASH_SIZE -> ResponseCodeEnum.INVALID_LIVE_HASH_SIZE;
            case EMPTY_QUERY_BODY -> ResponseCodeEnum.EMPTY_QUERY_BODY;
            case EMPTY_LIVE_HASH_QUERY -> ResponseCodeEnum.EMPTY_LIVE_HASH_QUERY;
            case LIVE_HASH_NOT_FOUND -> ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
            case ACCOUNT_ID_DOES_NOT_EXIST -> ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
            case LIVE_HASH_ALREADY_EXISTS -> ResponseCodeEnum.LIVE_HASH_ALREADY_EXISTS;
            case INVALID_FILE_WACL -> ResponseCodeEnum.INVALID_FILE_WACL;
            case SERIALIZATION_FAILED -> ResponseCodeEnum.SERIALIZATION_FAILED;
            case TRANSACTION_OVERSIZE -> ResponseCodeEnum.TRANSACTION_OVERSIZE;
            case TRANSACTION_TOO_MANY_LAYERS -> ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
            case CONTRACT_DELETED -> ResponseCodeEnum.CONTRACT_DELETED;
            case PLATFORM_NOT_ACTIVE -> ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
            case KEY_PREFIX_MISMATCH -> ResponseCodeEnum.KEY_PREFIX_MISMATCH;
            case PLATFORM_TRANSACTION_NOT_CREATED -> ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
            case INVALID_RENEWAL_PERIOD -> ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
            case INVALID_PAYER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
            case ACCOUNT_DELETED -> ResponseCodeEnum.ACCOUNT_DELETED;
            case FILE_DELETED -> ResponseCodeEnum.FILE_DELETED;
            case ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS -> ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            case SETTING_NEGATIVE_ACCOUNT_BALANCE -> ResponseCodeEnum.SETTING_NEGATIVE_ACCOUNT_BALANCE;
            case OBTAINER_REQUIRED -> ResponseCodeEnum.OBTAINER_REQUIRED;
            case OBTAINER_SAME_CONTRACT_ID -> ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
            case OBTAINER_DOES_NOT_EXIST -> ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
            case MODIFYING_IMMUTABLE_CONTRACT -> ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
            case FILE_SYSTEM_EXCEPTION -> ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
            case AUTORENEW_DURATION_NOT_IN_RANGE -> ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
            case ERROR_DECODING_BYTESTRING -> ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
            case CONTRACT_FILE_EMPTY -> ResponseCodeEnum.CONTRACT_FILE_EMPTY;
            case CONTRACT_BYTECODE_EMPTY -> ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
            case INVALID_INITIAL_BALANCE -> ResponseCodeEnum.INVALID_INITIAL_BALANCE;
            case INVALID_RECEIVE_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
            case INVALID_SEND_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
            case ACCOUNT_IS_NOT_GENESIS_ACCOUNT -> ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
            case PAYER_ACCOUNT_UNAUTHORIZED -> ResponseCodeEnum.PAYER_ACCOUNT_UNAUTHORIZED;
            case INVALID_FREEZE_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
            case FREEZE_TRANSACTION_BODY_NOT_FOUND -> ResponseCodeEnum.FREEZE_TRANSACTION_BODY_NOT_FOUND;
            case TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case RESULT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
            case NOT_SPECIAL_ACCOUNT -> ResponseCodeEnum.NOT_SPECIAL_ACCOUNT;
            case CONTRACT_NEGATIVE_GAS -> ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
            case CONTRACT_NEGATIVE_VALUE -> ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
            case INVALID_FEE_FILE -> ResponseCodeEnum.INVALID_FEE_FILE;
            case INVALID_EXCHANGE_RATE_FILE -> ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE;
            case INSUFFICIENT_LOCAL_CALL_GAS -> ResponseCodeEnum.INSUFFICIENT_LOCAL_CALL_GAS;
            case ENTITY_NOT_ALLOWED_TO_DELETE -> ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
            case AUTHORIZATION_FAILED -> ResponseCodeEnum.AUTHORIZATION_FAILED;
            case FILE_UPLOADED_PROTO_INVALID -> ResponseCodeEnum.FILE_UPLOADED_PROTO_INVALID;
            case FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK -> ResponseCodeEnum.FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK;
            case FEE_SCHEDULE_FILE_PART_UPLOADED -> ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
            case EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED -> ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
            case MAX_CONTRACT_STORAGE_EXCEEDED -> ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
            case TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT -> ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
            case TOTAL_LEDGER_BALANCE_INVALID -> ResponseCodeEnum.TOTAL_LEDGER_BALANCE_INVALID;
            case EXPIRATION_REDUCTION_NOT_ALLOWED -> ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
            case MAX_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
            case MAX_FILE_SIZE_EXCEEDED -> ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
            case RECEIVER_SIG_REQUIRED -> ResponseCodeEnum.RECEIVER_SIG_REQUIRED;
            case INVALID_TOPIC_ID -> ResponseCodeEnum.INVALID_TOPIC_ID;
            case INVALID_ADMIN_KEY -> ResponseCodeEnum.INVALID_ADMIN_KEY;
            case INVALID_SUBMIT_KEY -> ResponseCodeEnum.INVALID_SUBMIT_KEY;
            case UNAUTHORIZED -> ResponseCodeEnum.UNAUTHORIZED;
            case INVALID_TOPIC_MESSAGE -> ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
            case INVALID_AUTORENEW_ACCOUNT -> ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
            case AUTORENEW_ACCOUNT_NOT_ALLOWED -> ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
            case TOPIC_EXPIRED -> ResponseCodeEnum.TOPIC_EXPIRED;
            case INVALID_CHUNK_NUMBER -> ResponseCodeEnum.INVALID_CHUNK_NUMBER;
            case INVALID_CHUNK_TRANSACTION_ID -> ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
            case ACCOUNT_FROZEN_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
            case TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            case INVALID_TOKEN_ID -> ResponseCodeEnum.INVALID_TOKEN_ID;
            case INVALID_TOKEN_DECIMALS -> ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
            case INVALID_TOKEN_INITIAL_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
            case INVALID_TREASURY_ACCOUNT_FOR_TOKEN -> ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
            case INVALID_TOKEN_SYMBOL -> ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
            case TOKEN_HAS_NO_FREEZE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
            case TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN -> ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
            case MISSING_TOKEN_SYMBOL -> ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
            case TOKEN_SYMBOL_TOO_LONG -> ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
            case ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
            case TOKEN_HAS_NO_KYC_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
            case INSUFFICIENT_TOKEN_BALANCE -> ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
            case TOKEN_WAS_DELETED -> ResponseCodeEnum.TOKEN_WAS_DELETED;
            case TOKEN_HAS_NO_SUPPLY_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
            case TOKEN_HAS_NO_WIPE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
            case INVALID_TOKEN_MINT_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
            case INVALID_TOKEN_BURN_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
            case TOKEN_NOT_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            case CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT -> ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
            case INVALID_KYC_KEY -> ResponseCodeEnum.INVALID_KYC_KEY;
            case INVALID_WIPE_KEY -> ResponseCodeEnum.INVALID_WIPE_KEY;
            case INVALID_FREEZE_KEY -> ResponseCodeEnum.INVALID_FREEZE_KEY;
            case INVALID_SUPPLY_KEY -> ResponseCodeEnum.INVALID_SUPPLY_KEY;
            case MISSING_TOKEN_NAME -> ResponseCodeEnum.MISSING_TOKEN_NAME;
            case TOKEN_NAME_TOO_LONG -> ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
            case INVALID_WIPING_AMOUNT -> ResponseCodeEnum.INVALID_WIPING_AMOUNT;
            case TOKEN_IS_IMMUTABLE -> ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
            case TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            case TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
            case ACCOUNT_IS_TREASURY -> ResponseCodeEnum.ACCOUNT_IS_TREASURY;
            case TOKEN_ID_REPEATED_IN_TOKEN_LIST -> ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
            case TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case EMPTY_TOKEN_TRANSFER_BODY -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_BODY;
            case EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
            case INVALID_SCHEDULE_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ID;
            case SCHEDULE_IS_IMMUTABLE -> ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
            case INVALID_SCHEDULE_PAYER_ID -> ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
            case INVALID_SCHEDULE_ACCOUNT_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
            case NO_NEW_VALID_SIGNATURES -> ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
            case UNRESOLVABLE_REQUIRED_SIGNERS -> ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
            case SCHEDULED_TRANSACTION_NOT_IN_WHITELIST -> ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
            case SOME_SIGNATURES_WERE_INVALID -> ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
            case TRANSACTION_ID_FIELD_NOT_ALLOWED -> ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
            case IDENTICAL_SCHEDULE_ALREADY_CREATED -> ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
            case INVALID_ZERO_BYTE_IN_STRING -> ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
            case SCHEDULE_ALREADY_DELETED -> ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
            case SCHEDULE_ALREADY_EXECUTED -> ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
            case MESSAGE_SIZE_TOO_LARGE -> ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
            case OPERATION_REPEATED_IN_BUCKET_GROUPS -> ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
            case BUCKET_CAPACITY_OVERFLOW -> ResponseCodeEnum.BUCKET_CAPACITY_OVERFLOW;
            case NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION -> ResponseCodeEnum.NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
            case BUCKET_HAS_NO_THROTTLE_GROUPS -> ResponseCodeEnum.BUCKET_HAS_NO_THROTTLE_GROUPS;
            case THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC -> ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
            case SUCCESS_BUT_MISSING_EXPECTED_OPERATION -> ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            case UNPARSEABLE_THROTTLE_DEFINITIONS -> ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;
            case INVALID_THROTTLE_DEFINITIONS -> ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
            case ACCOUNT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
            case INVALID_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
            case INVALID_TOKEN_NFT_SERIAL_NUMBER -> ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
            case INVALID_NFT_ID -> ResponseCodeEnum.INVALID_NFT_ID;
            case METADATA_TOO_LONG -> ResponseCodeEnum.METADATA_TOO_LONG;
            case BATCH_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
            case INVALID_QUERY_RANGE -> ResponseCodeEnum.INVALID_QUERY_RANGE;
            case FRACTION_DIVIDES_BY_ZERO -> ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
            case INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
            case CUSTOM_FEES_LIST_TOO_LONG -> ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
            case INVALID_CUSTOM_FEE_COLLECTOR -> ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
            case INVALID_TOKEN_ID_IN_CUSTOM_FEES -> ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
            case TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
            case TOKEN_MAX_SUPPLY_REACHED -> ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
            case SENDER_DOES_NOT_OWN_NFT_SERIAL_NO -> ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            case CUSTOM_FEE_NOT_FULLY_SPECIFIED -> ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
            case CUSTOM_FEE_MUST_BE_POSITIVE -> ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
            case TOKEN_HAS_NO_FEE_SCHEDULE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
            case CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE -> ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
            case ROYALTY_FRACTION_CANNOT_EXCEED_ONE -> ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
            case FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT -> ResponseCodeEnum.FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
            case CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES -> ResponseCodeEnum.CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
            case CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON -> ResponseCodeEnum.CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
            case CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case INVALID_CUSTOM_FEE_SCHEDULE_KEY -> ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
            case INVALID_TOKEN_MINT_METADATA -> ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
            case INVALID_TOKEN_BURN_METADATA -> ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
            case CURRENT_TREASURY_STILL_OWNS_NFTS -> ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
            case ACCOUNT_STILL_OWNS_NFTS -> ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
            case TREASURY_MUST_OWN_BURNED_NFT -> ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
            case ACCOUNT_DOES_NOT_OWN_WIPED_NFT -> ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
            case ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED -> ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
            case PAYER_ACCOUNT_DELETED -> ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH -> ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS -> ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
            case INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
            case SERIAL_NUMBER_LIMIT_REACHED -> ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
            case CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE -> ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
            case NO_REMAINING_AUTOMATIC_ASSOCIATIONS -> ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            case EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT -> ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
            case REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT -> ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
            case TOKEN_IS_PAUSED -> ResponseCodeEnum.TOKEN_IS_PAUSED;
            case TOKEN_HAS_NO_PAUSE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
            case INVALID_PAUSE_KEY -> ResponseCodeEnum.INVALID_PAUSE_KEY;
            case FREEZE_UPDATE_FILE_DOES_NOT_EXIST -> ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
            case FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH -> ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
            case NO_UPGRADE_HAS_BEEN_PREPARED -> ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
            case NO_FREEZE_IS_SCHEDULED -> ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
            case UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE -> ResponseCodeEnum.UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
            case FREEZE_START_TIME_MUST_BE_FUTURE -> ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
            case PREPARED_UPDATE_FILE_IS_IMMUTABLE -> ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
            case FREEZE_ALREADY_SCHEDULED -> ResponseCodeEnum.FREEZE_ALREADY_SCHEDULED;
            case FREEZE_UPGRADE_IN_PROGRESS -> ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
            case UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;
            case UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
            case CONSENSUS_GAS_EXHAUSTED -> ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
            case REVERTED_SUCCESS -> ResponseCodeEnum.REVERTED_SUCCESS;
            case MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED -> ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
            case INVALID_ALIAS_KEY -> ResponseCodeEnum.INVALID_ALIAS_KEY;
            case UNEXPECTED_TOKEN_DECIMALS -> ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
            case INVALID_PROXY_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PROXY_ACCOUNT_ID;
            case INVALID_TRANSFER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
            case INVALID_FEE_COLLECTOR_ACCOUNT_ID -> ResponseCodeEnum.INVALID_FEE_COLLECTOR_ACCOUNT_ID;
            case ALIAS_IS_IMMUTABLE -> ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
            case SPENDER_ACCOUNT_SAME_AS_OWNER -> ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
            case AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
            case NEGATIVE_ALLOWANCE_AMOUNT -> ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
            case CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON -> ResponseCodeEnum.CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
            case SPENDER_DOES_NOT_HAVE_ALLOWANCE -> ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
            case AMOUNT_EXCEEDS_ALLOWANCE -> ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
            case MAX_ALLOWANCES_EXCEEDED -> ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
            case EMPTY_ALLOWANCES -> ResponseCodeEnum.EMPTY_ALLOWANCES;
            case SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES -> ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
            case REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES -> ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
            case FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES -> ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            case NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES -> ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
            case INVALID_ALLOWANCE_OWNER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
            case INVALID_ALLOWANCE_SPENDER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
            case REPEATED_ALLOWANCES_TO_DELETE -> ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
            case INVALID_DELEGATING_SPENDER -> ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
            case DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL -> ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
            case DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL -> ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
            case SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE -> ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
            case SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME -> ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
            case SCHEDULE_FUTURE_THROTTLE_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
            case SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
            case INVALID_ETHEREUM_TRANSACTION -> ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
            case WRONG_CHAIN_ID -> ResponseCodeEnum.WRONG_CHAIN_ID;
            case WRONG_NONCE -> ResponseCodeEnum.WRONG_NONCE;
            case ACCESS_LIST_UNSUPPORTED -> ResponseCodeEnum.ACCESS_LIST_UNSUPPORTED;
            case SCHEDULE_PENDING_EXPIRATION -> ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
            case CONTRACT_IS_TOKEN_TREASURY -> ResponseCodeEnum.CONTRACT_IS_TOKEN_TREASURY;
            case CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES;
            case CONTRACT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
            case CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT -> ResponseCodeEnum.CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT;
            case PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION -> ResponseCodeEnum.PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION;
            case PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED -> ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
            case SELF_STAKING_IS_NOT_ALLOWED -> ResponseCodeEnum.SELF_STAKING_IS_NOT_ALLOWED;
            case INVALID_STAKING_ID -> ResponseCodeEnum.INVALID_STAKING_ID;
            case STAKING_NOT_ENABLED -> ResponseCodeEnum.STAKING_NOT_ENABLED;
            case INVALID_PRNG_RANGE -> ResponseCodeEnum.INVALID_PRNG_RANGE;
            case MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED -> ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
            case INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE -> ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
            case INSUFFICIENT_BALANCES_FOR_STORAGE_RENT -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
            case MAX_CHILD_RECORDS_EXCEEDED -> ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
            case INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;
            case TRANSACTION_HAS_UNKNOWN_FIELDS -> ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
            case ACCOUNT_IS_IMMUTABLE -> ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
            case ALIAS_ALREADY_ASSIGNED -> ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
            case UNRECOGNIZED -> throw new RuntimeException("UNRECOGNIZED Response code!");
        };
    }

    public boolean failed() {
        return failureReason != null;
    }

    public static KeyOrLookupFailureReason withFailureReason(final ResponseCodeEnum response) {
        return new KeyOrLookupFailureReason(null, response);
    }

    public static KeyOrLookupFailureReason withKey(final HederaKey key) {
        return new KeyOrLookupFailureReason(key, null);
    }
}
