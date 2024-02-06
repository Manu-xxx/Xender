package com.swirlds.baseapi.persistence;

import com.swirlds.baseapi.domain.Wallet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WalletDao {

    private static class InstanceHolder {
        private static final WalletDao INSTANCE = new WalletDao();
    }

    public static @NonNull WalletDao getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static final Map<String, Wallet> WALLET_REPOSITORY = new ConcurrentHashMap<>();

    public Wallet save(Wallet wallet) {
        if (WALLET_REPOSITORY.containsKey(wallet.address())) {
            throw new IllegalArgumentException("Resource already exist");
        }
        WALLET_REPOSITORY.put(wallet.address(), wallet);
        return wallet;
    }


    public Wallet findById(String id) {
        return WALLET_REPOSITORY.get(id);
    }

    public void deleteById(String id) {
        if (!WALLET_REPOSITORY.containsKey(id)) {
            throw new IllegalArgumentException("Resource does not exist");
        }
        WALLET_REPOSITORY.remove(id);
    }

    public List<Wallet> findAll() {
        return WALLET_REPOSITORY.values().stream().toList();
    }
}
