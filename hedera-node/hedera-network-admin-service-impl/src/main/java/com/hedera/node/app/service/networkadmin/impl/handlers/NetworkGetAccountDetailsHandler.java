/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenFreezeStatus;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenKycStatus;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.hapi.node.token.GetAccountDetailsQuery;
import com.hedera.hapi.node.token.GetAccountDetailsResponse;
import com.hedera.hapi.node.token.GrantedCryptoAllowance;
import com.hedera.hapi.node.token.GrantedNftAllowance;
import com.hedera.hapi.node.token.GrantedTokenAllowance;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.networkadmin.impl.config.NetworkAdminServiceConfig;
import com.hedera.node.app.service.networkadmin.impl.utils.NetworkAdminServiceUtil;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableTokenStore.TokenMetadata;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#GET_ACCOUNT_DETAILS}.
 */
@Singleton
public class NetworkGetAccountDetailsHandler extends PaidQueryHandler {

    private final NetworkInfo networkInfo;

    @Inject
    public NetworkGetAccountDetailsHandler() {
        // Exists for injection
        this.networkInfo = null;
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.accountDetailsOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = GetAccountDetailsResponse.newBuilder().header(header);
        return Response.newBuilder().accountDetails(response).build();
    }

    @Override
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final GetAccountDetailsQuery op = query.accountDetailsOrThrow();
        if (op.hasAccountId()) {
            final var accountMetadata = accountStore.getAccountById(op.accountIdOrElse(AccountID.DEFAULT));
            mustExist(accountMetadata, INVALID_ACCOUNT_ID);
            if (accountMetadata.deleted()) {
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            }
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var op = query.accountDetailsOrThrow();
        final var responseBuilder = GetAccountDetailsResponse.newBuilder();
        final var account = op.accountIdOrElse(AccountID.DEFAULT);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var networkAdminConfig = context.configuration().getConfigData(NetworkAdminServiceConfig.class);
            final var readableTokenStore = context.createStore(ReadableTokenStore.class);
            final var tokenRelationStore = context.createStore(ReadableTokenRelationStore.class);
            final var optionalInfo =
                    infoForAccount(account, accountStore, networkAdminConfig, readableTokenStore, tokenRelationStore);
            optionalInfo.ifPresent(responseBuilder::accountDetails);
        }

        return Response.newBuilder().accountDetails(responseBuilder).build();
    }

    /**
     * Provides information about an account.
     * @param accountID the account to get information about
     * @param accountStore the account store
     * @return the information about the account
     */
    private Optional<AccountDetails> infoForAccount(
            @NonNull final AccountID accountID,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final NetworkAdminServiceConfig networkAdminConfig,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore) {
        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            return Optional.empty();
        } else {
            final var info = AccountDetails.newBuilder();
            info.accountId(
                    AccountID.newBuilder().accountNum(account.accountNumber()).build());
            info.contractAccountId(NetworkAdminServiceUtil.asHexedEvmAddress(accountID));
            info.deleted(account.deleted());
            if (!isEmpty(account.key())) info.key(account.key());
            info.balance(account.tinybarBalance());
            info.receiverSigRequired(account.receiverSigRequired());
            info.expirationTime(Timestamp.newBuilder().seconds(account.expiry()).build());
            info.autoRenewPeriod(
                    Duration.newBuilder().seconds(account.autoRenewSecs()).build());
            info.memo(account.memo());
            info.ownedNfts(account.numberOwnedNfts());
            info.maxAutomaticTokenAssociations(account.maxAutoAssociations());
            info.alias(account.alias());
            info.ledgerId(networkInfo.ledgerId());
            info.grantedCryptoAllowances(getCryptoGrantedAllowancesList(account));
            info.grantedNftAllowances(getNftGrantedAllowancesList(account));
            info.grantedTokenAllowances(getFungibleGrantedTokenAllowancesList(account));

            final var tokenRels = getTokenRelationships(
                    networkAdminConfig.maxTokensForAccountInfo(), account, readableTokenStore, tokenRelationStore);
            if (!tokenRels.isEmpty()) {
                info.tokenRelationships(tokenRels);
            }
            return Optional.of(info.build());
        }
    }

    private List<TokenRelationship> getTokenRelationships(
            final int maxRelationships,
            Account account,
            ReadableTokenStore readableTokenStore,
            ReadableTokenRelationStore tokenRelationStore) {
        final var tokenRelationshipList = new ArrayList<TokenRelationship>();
        var tokenNum = account.headTokenNumber();
        int count = 0;

        while (tokenNum != 0 && count <= maxRelationships) {
            final Optional<TokenRelation> optionalTokenRelation = tokenRelationStore.get(
                    AccountID.newBuilder().accountNum(account.accountNumber()).build(),
                    TokenID.newBuilder().tokenNum(tokenNum).build());
            if (optionalTokenRelation.isPresent()) {
                final var tokenId = TokenID.newBuilder()
                        .shardNum(STATIC_PROPERTIES.getShard())
                        .realmNum(STATIC_PROPERTIES.getRealm())
                        .tokenNum(tokenNum)
                        .build();
                final TokenMetadata token = readableTokenStore.getTokenMeta(tokenId);
                final var tokenRelation = optionalTokenRelation.get();
                if (token != null) {
                    final TokenRelationship tokenRelationship = TokenRelationship.newBuilder()
                            .tokenId(tokenId)
                            .balance(tokenRelation.balance())
                            .decimals(token.decimals())
                            .symbol(token.symbol())
                            .kycStatus(
                                    tokenRelation.kycGranted()
                                            ? TokenKycStatus.GRANTED
                                            : TokenKycStatus.KYC_NOT_APPLICABLE)
                            .freezeStatus(
                                    tokenRelation.frozen() ? TokenFreezeStatus.FROZEN : TokenFreezeStatus.UNFROZEN)
                            .automaticAssociation(tokenRelation.automaticAssociation())
                            .build();
                    tokenRelationshipList.add(tokenRelationship);
                }
                tokenNum = tokenRelation.nextToken();
            } else {
                break;
            }
            count++;
        }
        return tokenRelationshipList;
    }

    private List<GrantedNftAllowance> getNftGrantedAllowancesList(final Account account) {
        if (!account.approveForAllNftAllowances().isEmpty()) {
            List<GrantedNftAllowance> nftAllowances = new ArrayList<>();
            for (var a : account.approveForAllNftAllowances()) {
                final var approveForAllNftsAllowance = GrantedNftAllowance.newBuilder();
                approveForAllNftsAllowance.tokenId(
                        TokenID.newBuilder().tokenNum(a.tokenNum()).build());
                approveForAllNftsAllowance.spender(
                        AccountID.newBuilder().accountNum(a.spenderNum()).build());
                nftAllowances.add(approveForAllNftsAllowance.build());
            }
            return nftAllowances;
        }
        return Collections.emptyList();
    }

    private static List<GrantedTokenAllowance> getFungibleGrantedTokenAllowancesList(final Account account) {
        if (!account.tokenAllowances().isEmpty()) {
            List<GrantedTokenAllowance> tokenAllowances = new ArrayList<>();
            final var tokenAllowance = GrantedTokenAllowance.newBuilder();
            for (var a : account.tokenAllowances()) {
                tokenAllowance.tokenId(
                        TokenID.newBuilder().tokenNum(a.tokenNum()).build());
                tokenAllowance.spender(
                        AccountID.newBuilder().accountNum(a.spenderNum()).build());
                tokenAllowance.amount(a.amount());
                tokenAllowances.add(tokenAllowance.build());
            }
            return tokenAllowances;
        }
        return Collections.emptyList();
    }

    private static List<GrantedCryptoAllowance> getCryptoGrantedAllowancesList(final Account account) {
        if (!account.cryptoAllowances().isEmpty()) {
            List<GrantedCryptoAllowance> cryptoAllowances = new ArrayList<>();
            final var cryptoAllowance = GrantedCryptoAllowance.newBuilder();
            for (var a : account.cryptoAllowances()) {
                cryptoAllowance.spender(
                        AccountID.newBuilder().accountNum(a.spenderNum()).build());
                cryptoAllowance.amount(a.amount());
                cryptoAllowances.add(cryptoAllowance.build());
            }
            return cryptoAllowances;
        }
        return Collections.emptyList();
    }
}
