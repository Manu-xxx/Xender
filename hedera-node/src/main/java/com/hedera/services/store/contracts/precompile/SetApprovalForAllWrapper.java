package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;

public record SetApprovalForAllWrapper(AccountID to, boolean approved) {
}
