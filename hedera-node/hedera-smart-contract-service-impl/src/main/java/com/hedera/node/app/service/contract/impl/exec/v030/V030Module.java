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

package com.hedera.node.app.service.contract.impl.exec.v030;

import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.INITIAL_CONTRACT_NONCE;
import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.REQUIRE_CODE_DEPOSIT_TO_SUCCEED;
import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

import com.hedera.node.app.service.contract.impl.annotations.ServicesV030;
import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomBalanceOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCallOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomChainIdOperation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCreate2Operation;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomCreateOperation;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomContractCreationProcessor;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

/**
 * Provides the Services 0.30 EVM implementation, which consists of London operations and
 * Instanbul precompiles plus the Hedera gas calculator, system contracts, and operations
 * as they were configured in the 0.30 release (in particular, without lazy creation and
 * without special treatment to make system addresses "invisible").
 */
@Module
public interface V030Module {
    @Provides
    @Singleton
    @ServicesV030
    static TransactionProcessor provideTransactionProcessor(
            @ServicesV030 @NonNull final CustomMessageCallProcessor messageCallProcessor,
            @ServicesV030 @NonNull final ContractCreationProcessor contractCreationProcessor,
            @NonNull final CustomGasCharging gasCharging) {
        return new TransactionProcessor(gasCharging, messageCallProcessor, contractCreationProcessor);
    }

    @Provides
    @Singleton
    @ServicesV030
    static ContractCreationProcessor provideContractCreationProcessor(
            @ServicesV030 @NonNull final EVM evm,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final Set<ContractValidationRule> validationRules) {
        return new CustomContractCreationProcessor(
                evm,
                gasCalculator,
                REQUIRE_CODE_DEPOSIT_TO_SUCCEED,
                List.copyOf(validationRules),
                INITIAL_CONTRACT_NONCE);
    }

    @Provides
    @Singleton
    @ServicesV030
    static CustomMessageCallProcessor provideMessageCallProcessor(
            @ServicesV030 @NonNull final EVM evm,
            @ServicesV030 @NonNull final FeatureFlags featureFlags,
            @ServicesV030 @NonNull final AddressChecks addressChecks,
            @ServicesV030 @NonNull final PrecompileContractRegistry registry,
            @NonNull final Map<Address, PrecompiledContract> hederaPrecompiles) {
        return new CustomMessageCallProcessor(evm, featureFlags, registry, addressChecks, hederaPrecompiles);
    }

    @Provides
    @Singleton
    @ServicesV030
    static EVM provideEVM(
            @ServicesV030 @NonNull final Set<Operation> customOperations, @NonNull final GasCalculator gasCalculator) {
        // Use London EVM with 0.30 custom operations and 0x00 chain id (set at runtime)
        final var operationRegistry = new OperationRegistry();
        registerLondonOperations(operationRegistry, gasCalculator, BigInteger.ZERO);
        customOperations.forEach(operationRegistry::put);
        return new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
    }

    @Provides
    @Singleton
    @ServicesV030
    static PrecompileContractRegistry providePrecompileContractRegistry(@NonNull final GasCalculator gasCalculator) {
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);
        return precompileContractRegistry;
    }

    @Binds
    @ServicesV030
    FeatureFlags bindFeatureFlags(Version030FeatureFlags featureFlags);

    @Binds
    @ServicesV030
    AddressChecks bindAddressChecks(Version030AddressChecks addressChecks);

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideBalanceOperation(
            @NonNull final GasCalculator gasCalculator, @ServicesV030 @NonNull final AddressChecks addressChecks) {
        return new CustomBalanceOperation(gasCalculator, addressChecks);
    }

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideChainIdOperation(@NonNull final GasCalculator gasCalculator) {
        return new CustomChainIdOperation(gasCalculator);
    }

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideCallOperation(
            @NonNull final GasCalculator gasCalculator,
            @ServicesV030 @NonNull final FeatureFlags featureFlags,
            @ServicesV030 @NonNull final AddressChecks addressChecks) {
        return new CustomCallOperation(featureFlags, gasCalculator, addressChecks);
    }

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideCreateOperation(@NonNull final GasCalculator gasCalculator) {
        return new CustomCreateOperation(gasCalculator);
    }

    @Provides
    @IntoSet
    @ServicesV030
    static Operation provideCreate2Operation(
            @NonNull final GasCalculator gasCalculator, @ServicesV030 @NonNull final FeatureFlags featureFlags) {
        return new CustomCreate2Operation(gasCalculator, featureFlags);
    }
}
