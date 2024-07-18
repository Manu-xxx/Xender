/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.crypto.tss.impl;

import com.swirlds.crypto.signaturescheme.api.PairingPrivateKey;
import com.swirlds.crypto.signaturescheme.api.PairingPublicKey;
import com.swirlds.crypto.signaturescheme.api.PairingSignature;
import com.swirlds.crypto.tss.api.TssMessage;
import com.swirlds.crypto.tss.api.TssParticipantDirectory;
import com.swirlds.crypto.tss.api.TssPrivateShare;
import com.swirlds.crypto.tss.api.TssPublicShare;
import com.swirlds.crypto.tss.api.TssService;
import com.swirlds.crypto.tss.api.TssShareSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A mock implementation of the TssService.
 */
public class TssServiceImpl implements TssService {

    @NonNull
    @Override
    public TssMessage generateTssMessages(
            @NonNull TssParticipantDirectory pendingParticipantDirectory, @NonNull TssPrivateShare privateShare) {
        return new TssMessage(new byte[] {});
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull TssParticipantDirectory pendingParticipantDirectory) {
        return new TssMessage(new byte[] {});
    }

    @Override
    public boolean verifyTssMessage(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull TssMessage tssMessages) {
        return true;
    }

    @Nullable
    @Override
    public List<TssPrivateShare> decryptPrivateShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validTssMessages) {
        return participantDirectory.getOwnedShareIds().stream()
                .map(sid -> new TssPrivateShare(sid, new PairingPrivateKey()))
                .toList();
    }

    @NonNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NonNull List<TssPrivateShare> privateShares) {
        return new PairingPrivateKey();
    }

    @Nullable
    @Override
    public List<TssPublicShare> computePublicShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> tssMessages) {
        return participantDirectory.getShareIds().stream()
                .map(sid -> new TssPublicShare(sid, new PairingPublicKey()))
                .toList();
    }

    @NonNull
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull List<TssPublicShare> publicShares) {
        return new PairingPublicKey();
    }

    @NonNull
    @Override
    public TssShareSignature sign(@NonNull TssPrivateShare privateShare, @NonNull byte[] message) {
        return new TssShareSignature(privateShare.shareId(), new PairingSignature());
    }

    @Override
    public boolean verifySignature(
            @NonNull TssParticipantDirectory participantDirectory,
            @NonNull List<TssPublicShare> publicShares,
            @NonNull TssShareSignature signature) {
        return true;
    }

    @NonNull
    @Override
    public PairingSignature aggregateSignatures(@NonNull List<TssShareSignature> partialSignatures) {
        return new PairingSignature();
    }
}
