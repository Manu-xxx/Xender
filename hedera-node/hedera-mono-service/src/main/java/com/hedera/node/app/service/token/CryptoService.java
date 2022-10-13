package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;

import javax.annotation.Nonnull;

/**
 * The {@code CryptoService} is responsible for working with {Account}s.
 * It implements all transactions and queries defined in the "CryptoService" protobuf
 * service. The {@code CryptoService} is used extensively by the core application workflows
 * to implement transaction handling, since all transactions and most queries involve payments
 * and thus the transfer of HBAR from one account to another. A {@link CryptoPreTransactionHandler}
 * contains API for all transactions related to crypto (and token) transfers, as well as some
 * additional API needed by the core application to apply payments and compute rewards.
 */
public interface CryptoService extends Service {
	/**
	 * Creates and returns a new {@link CryptoPreTransactionHandler}
	 *
	 * @return A new {@link CryptoPreTransactionHandler}
	 */
	@Override
	@Nonnull
	CryptoPreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
