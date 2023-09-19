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

package com.hedera.node.app.service.contract.impl.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals.DecimalsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.pauses.PausesTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri.TokenUriTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Provides the {@link HtsCallTranslator} implementations for the HTS system contract.
 */
@Module
public interface HtsTranslatorsModule {
    @Provides
    @Singleton
    static List<HtsCallTranslator> provideCallAttemptTranslators(@NonNull final Set<HtsCallTranslator> translators) {
        return List.copyOf(translators);
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideAssociationsTranslator(@NonNull final AssociationsTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideErc20TransfersTranslator(@NonNull final Erc20TransfersTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideErc721TransferFromTranslator(
            @NonNull final Erc721TransferFromTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideClassicTransfersTranslator(@NonNull final ClassicTransfersTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideMintTranslator(@NonNull final MintTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideBalanceOfTranslator(@NonNull final BalanceOfTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideIsApprovedForAllTranslator(@NonNull final IsApprovedForAllTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideNameTranslator(@NonNull final NameTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideSymbolTranslator(@NonNull final SymbolTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTotalSupplyTranslator(@NonNull final TotalSupplyTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideOwnerOfTranslator(@NonNull final OwnerOfTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideSetApprovalForAllTranslator(@NonNull final SetApprovalForAllTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideDecimalsTranslator(@NonNull final DecimalsTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideTokenUriTranslator(@NonNull final TokenUriTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator providePausesTranslator(@NonNull final PausesTranslator translator) {
        return translator;
    }

    @Provides
    @Singleton
    @IntoSet
    static HtsCallTranslator provideWipeTranslator(@NonNull final WipeTranslator translator) {
        return translator;
    }
}
