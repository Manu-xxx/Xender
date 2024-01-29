/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Contains configuration values for the platform schedulers.
 *
 * @param eventHasherUnhandledCapacity                      number of unhandled tasks allowed in the event hasher
 *                                                          scheduler
 * @param internalEventValidatorSchedulerType               the internal event validator scheduler type
 * @param internalEventValidatorUnhandledCapacity           number of unhandled events allowed in the internal event
 *                                                          validator scheduler
 * @param eventDeduplicatorSchedulerType                    the event deduplicator scheduler type
 * @param eventDeduplicatorUnhandledCapacity                number of unhandled tasks allowed in the event deduplicator
 *                                                          scheduler
 * @param eventSignatureValidatorSchedulerType              the event signature validator scheduler type
 * @param eventSignatureValidatorUnhandledCapacity          number of unhandled tasks allowed in the event signature
 *                                                          validator scheduler
 * @param orphanBufferSchedulerType                         the orphan buffer scheduler type
 * @param orphanBufferUnhandledCapacity                     number of unhandled tasks allowed in the orphan buffer
 *                                                          scheduler
 * @param inOrderLinkerSchedulerType                        the in-order linker scheduler type
 * @param inOrderLinkerUnhandledCapacity                    number of unhandled tasks allowed in the in-order linker
 *                                                          scheduler
 * @param linkedEventIntakeSchedulerType                    the linked event intake scheduler type
 * @param linkedEventIntakeUnhandledCapacity                number of unhandled tasks allowed in the linked event intake
 *                                                          scheduler
 * @param eventCreationManagerSchedulerType                 the event creation manager scheduler type
 * @param eventCreationManagerUnhandledCapacity             number of unhandled tasks allowed in the event creation
 *                                                          manager scheduler
 * @param signedStateFileManagerSchedulerType               the signed state file manager scheduler type
 * @param signedStateFileManagerUnhandledCapacity           number of unhandled tasks allowed in the signed state file
 *                                                          manager scheduler
 * @param stateSignerSchedulerType                          the state signer scheduler type
 * @param stateSignerUnhandledCapacity                      number of unhandled tasks allowed in the state signer
 *                                                          scheduler, default is -1 (unlimited)
 * @param pcesWriterSchedulerType                           the preconsensus event writer scheduler type
 * @param pcesWriterUnhandledCapacity                       number of unhandled tasks allowed in the preconsensus event
 *                                                          writer scheduler
 * @param pcesSequencerSchedulerType                        the preconsensus event sequencer scheduler type
 * @param pcesSequencerUnhandledTaskCapacity                number of unhandled tasks allowed in the preconsensus event
 *                                                          sequencer scheduler
 * @param eventDurabilityNexusSchedulerType                 the durability nexus scheduler type
 * @param eventDurabilityNexusUnhandledTaskCapacity         number of unhandled tasks allowed in the durability nexus
 *                                                          scheduler
 * @param applicationTransactionPrehandlerSchedulerType     the application transaction prehandler scheduler type
 * @param applicationTransactionPrehandlerUnhandledCapacity number of unhandled tasks allowed for the application
 *                                                          transaction prehandler
 * @param stateSignatureCollectorSchedulerType              the state signature collector scheduler type
 * @param stateSignatureCollectorUnhandledCapacity          number of unhandled tasks allowed for the state signature
 *                                                          collector
 * @param shadowgraphSchedulerType                          the shadowgraph scheduler type
 * @param shadowgraphUnhandledCapacity                      number of unhandled tasks allowed for the shadowgraph
 * @param issDetectorSchedulerType                          the ISS detector scheduler type
 * @param issDetectorUnhandledCapacity                      number of unhandled tasks allowed for the ISS detector
 */
@ConfigData("platformSchedulers")
public record PlatformSchedulersConfig(
        @ConfigProperty(defaultValue = "10000") int eventHasherUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType internalEventValidatorSchedulerType,
        @ConfigProperty(defaultValue = "500") int internalEventValidatorUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType eventDeduplicatorSchedulerType,
        @ConfigProperty(defaultValue = "500") int eventDeduplicatorUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType eventSignatureValidatorSchedulerType,
        @ConfigProperty(defaultValue = "500") int eventSignatureValidatorUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType orphanBufferSchedulerType,
        @ConfigProperty(defaultValue = "500") int orphanBufferUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType inOrderLinkerSchedulerType,
        @ConfigProperty(defaultValue = "500") int inOrderLinkerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") TaskSchedulerType linkedEventIntakeSchedulerType,
        @ConfigProperty(defaultValue = "500") int linkedEventIntakeUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType eventCreationManagerSchedulerType,
        @ConfigProperty(defaultValue = "500") int eventCreationManagerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") TaskSchedulerType signedStateFileManagerSchedulerType,
        @ConfigProperty(defaultValue = "20") int signedStateFileManagerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") TaskSchedulerType stateSignerSchedulerType,
        @ConfigProperty(defaultValue = "-1") int stateSignerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") TaskSchedulerType pcesWriterSchedulerType,
        @ConfigProperty(defaultValue = "500") int pcesWriterUnhandledCapacity,
        @ConfigProperty(defaultValue = "DIRECT") TaskSchedulerType pcesSequencerSchedulerType,
        @ConfigProperty(defaultValue = "-1") int pcesSequencerUnhandledTaskCapacity,
        @ConfigProperty(defaultValue = "DIRECT") TaskSchedulerType eventDurabilityNexusSchedulerType,
        @ConfigProperty(defaultValue = "-1") int eventDurabilityNexusUnhandledTaskCapacity,
        @ConfigProperty(defaultValue = "CONCURRENT") TaskSchedulerType applicationTransactionPrehandlerSchedulerType,
        @ConfigProperty(defaultValue = "500") int applicationTransactionPrehandlerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType stateSignatureCollectorSchedulerType,
        @ConfigProperty(defaultValue = "500") int stateSignatureCollectorUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType shadowgraphSchedulerType,
        @ConfigProperty(defaultValue = "500") int shadowgraphUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType issDetectorSchedulerType,
        @ConfigProperty(defaultValue = "500") int issDetectorUnhandledCapacity) {}
