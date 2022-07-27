package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

public class RevokeKycLogic {
  private final TypedTokenStore tokenStore;
  private final AccountStore accountStore;

  public RevokeKycLogic(final TypedTokenStore tokenStore, final AccountStore accountStore) {
    this.tokenStore = tokenStore;
    this.accountStore = accountStore;
  }

  public void revokeKyc(final Id targetTokenId, final Id targetAccountId) {
    /* --- Load the model objects --- */
    final var loadedToken = tokenStore.loadToken(targetTokenId);
    final var loadedAccount = accountStore.loadAccount(targetAccountId);
    final var tokenRelationship = tokenStore.loadTokenRelationship(loadedToken, loadedAccount);

    /* --- Do the business logic --- */
    tokenRelationship.changeKycState(false);

    /* --- Persist the updated models --- */
    tokenStore.commitTokenRelationships(List.of(tokenRelationship));
  }

  public ResponseCodeEnum validate(final TransactionBody txnBody) {
    TokenRevokeKycTransactionBody op = txnBody.getTokenRevokeKyc();

    if (!op.hasToken()) {
      return INVALID_TOKEN_ID;
    }

    if (!op.hasAccount()) {
      return INVALID_ACCOUNT_ID;
    }

    return OK;
  }
}
