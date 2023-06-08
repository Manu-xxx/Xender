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

package com.hedera.node.app.service.contract.impl.exec.gas;

import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

/**
 * Need to clean up and refactor {@link com.hedera.node.app.service.mono.contracts.gascalculator.GasCalculatorHederaV22}
 * to get its price and exchange rate from the transaction's {@link com.hedera.node.app.spi.meta.bni.Scope}.
 */
public class CustomGasCalculator extends LondonGasCalculator {}
