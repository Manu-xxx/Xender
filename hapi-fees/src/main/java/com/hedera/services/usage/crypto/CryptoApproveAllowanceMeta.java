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
package com.hedera.services.usage.crypto;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToCryptoMap;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToNftMap;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToTokenMap;
import static com.hedera.services.usage.crypto.CryptoContextUtils.countSerials;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Metadata for CryptoApproveAllowance */
public class CryptoApproveAllowanceMeta {
    private final long effectiveNow;
    private final long msgBytesUsed;
    private final Map<Long, Long> cryptoAllowances;
    private final Map<AllowanceId, Long> tokenAllowances;
    private final Set<AllowanceId> nftAllowances;

    public CryptoApproveAllowanceMeta(Builder builder) {
        effectiveNow = builder.effectiveNow;
        msgBytesUsed = builder.msgBytesUsed;
        cryptoAllowances = builder.cryptoAllowances;
        tokenAllowances = builder.tokenAllowances;
        nftAllowances = builder.nftAllowances;
    }

    public CryptoApproveAllowanceMeta(
            CryptoApproveAllowanceTransactionBody cryptoApproveTxnBody,
            long transactionValidStartSecs) {
        effectiveNow = transactionValidStartSecs;
        msgBytesUsed = bytesUsedInTxn(cryptoApproveTxnBody);
        cryptoAllowances = convertToCryptoMap(cryptoApproveTxnBody.getCryptoAllowancesList());
        tokenAllowances = convertToTokenMap(cryptoApproveTxnBody.getTokenAllowancesList());
        nftAllowances = convertToNftMap(cryptoApproveTxnBody.getNftAllowancesList());
    }

    private int bytesUsedInTxn(CryptoApproveAllowanceTransactionBody op) {
        return op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE
                + op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE
                + op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE
                + countSerials(op.getNftAllowancesList()) * LONG_SIZE;
    }

    public static Builder newBuilder() {
        return new CryptoApproveAllowanceMeta.Builder();
    }

    public long getEffectiveNow() {
        return effectiveNow;
    }

    public long getMsgBytesUsed() {
        return msgBytesUsed;
    }

    public Map<Long, Long> getCryptoAllowances() {
        return cryptoAllowances;
    }

    public Map<AllowanceId, Long> getTokenAllowances() {
        return tokenAllowances;
    }

    public Set<AllowanceId> getNftAllowances() {
        return nftAllowances;
    }

    public static class Builder {
        private long effectiveNow;
        private long msgBytesUsed;
        private Map<Long, Long> cryptoAllowances;
        private Map<AllowanceId, Long> tokenAllowances;
        private Set<AllowanceId> nftAllowances;

        public CryptoApproveAllowanceMeta.Builder cryptoAllowances(
                Map<Long, Long> cryptoAllowances) {
            this.cryptoAllowances = cryptoAllowances;
            return this;
        }

        public CryptoApproveAllowanceMeta.Builder tokenAllowances(
                Map<AllowanceId, Long> tokenAllowances) {
            this.tokenAllowances = tokenAllowances;
            return this;
        }

        public CryptoApproveAllowanceMeta.Builder nftAllowances(Set<AllowanceId> nftAllowances) {
            this.nftAllowances = nftAllowances;
            return this;
        }

        public CryptoApproveAllowanceMeta.Builder effectiveNow(long now) {
            this.effectiveNow = now;
            return this;
        }

        public CryptoApproveAllowanceMeta.Builder msgBytesUsed(long msgBytesUsed) {
            this.msgBytesUsed = msgBytesUsed;
            return this;
        }

        public Builder() {
            // empty here on purpose.
        }

        public CryptoApproveAllowanceMeta build() {
            return new CryptoApproveAllowanceMeta(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cryptoAllowances", cryptoAllowances)
                .add("tokenAllowances", tokenAllowances)
                .add("nftAllowances", nftAllowances)
                .add("effectiveNow", effectiveNow)
                .add("msgBytesUsed", msgBytesUsed)
                .toString();
    }
}
