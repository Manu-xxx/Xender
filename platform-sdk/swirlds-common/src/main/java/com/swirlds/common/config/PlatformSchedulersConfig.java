/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.config;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Contains configuration values for the platform schedulers.
 *
 * @param eventHasherSchedulerType                 the event hasher scheduler type
 * @param eventHasherUnhandledCapacity             number of unhandled tasks allowed in the event hasher scheduler
 * @param internalEventValidatorSchedulerType      the internal event validator scheduler type
 * @param internalEventValidatorUnhandledCapacity  number of unhandled events allowed in the internal event validator
 *                                                 scheduler
 * @param eventDeduplicatorSchedulerType           the event deduplicator scheduler type
 * @param eventDeduplicatorUnhandledCapacity       number of unhandled tasks allowed in the event deduplicator
 *                                                 scheduler
 * @param eventSignatureValidatorSchedulerType     the event signature validator scheduler type
 * @param eventSignatureValidatorUnhandledCapacity number of unhandled tasks allowed in the event signature validator
 *                                                 scheduler
 * @param orphanBufferSchedulerType                the orphan buffer scheduler type
 * @param orphanBufferUnhandledCapacity            number of unhandled tasks allowed in the orphan buffer scheduler
 * @param inOrderLinkerSchedulerType               the in-order linker scheduler type
 * @param inOrderLinkerUnhandledCapacity           number of unhandled tasks allowed in the in-order linker scheduler
 * @param linkedEventIntakeSchedulerType           the linked event intake scheduler type
 * @param linkedEventIntakeUnhandledCapacity       number of unhandled tasks allowed in the linked event intake
 *                                                 scheduler
 * @param eventCreationManagerSchedulerType        the event creation manager scheduler type
 * @param eventCreationManagerUnhandledCapacity    number of unhandled tasks allowed in the event creation manager
 *                                                 scheduler
 * @param signedStateFileManagerSchedulerType      the signed state file manager scheduler type
 * @param signedStateFileManagerUnhandledCapacity  number of unhandled tasks allowed in the signed state file manager
 *                                                 scheduler
 * @param stateSignerSchedulerType                 the state signer scheduler type
 * @param stateSignerUnhandledCapacity             number of unhandled tasks allowed in the state signer scheduler,
 *                                                 default is -1 (unlimited)
 * @param pcesWriterSchedulerType                  the preconsensus event writer scheduler type
 * @param pcesWriterUnhandledCapacity              number of unhandled tasks allowed in the preconsensus event writer scheduler
 */
@ConfigData("platformSchedulers")
public record PlatformSchedulersConfig(
        @ConfigProperty(defaultValue = "CONCURRENT") String eventHasherSchedulerType,
        @ConfigProperty(defaultValue = "500") int eventHasherUnhandledCapacity,
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
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType linkedEventIntakeSchedulerType,
        @ConfigProperty(defaultValue = "500") int linkedEventIntakeUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL") TaskSchedulerType eventCreationManagerSchedulerType,
        @ConfigProperty(defaultValue = "500") int eventCreationManagerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") String signedStateFileManagerSchedulerType,
        @ConfigProperty(defaultValue = "20") int signedStateFileManagerUnhandledCapacity,
        @ConfigProperty(defaultValue = "SEQUENTIAL_THREAD") String pcesWriterSchedulerType,
        @ConfigProperty(defaultValue = "500") int pcesWriterUnhandledCapacity) {

    /**
     * Get the event hasher scheduler type
     *
     * @return the event hasher scheduler type
     */
    public TaskSchedulerType getEventHasherSchedulerType() {
        return TaskSchedulerType.valueOf(eventHasherSchedulerType);
    }

    /**
     * Get the internal event validator scheduler type
     *
     * @return the internal event validator scheduler type
     */
    @NonNull
    public TaskSchedulerType getInternalEventValidatorSchedulerType() {
        return TaskSchedulerType.valueOf(internalEventValidatorSchedulerType);
    }

    /**
     * Get the event deduplicator scheduler type
     *
     * @return the event deduplicator scheduler type
     */
    @NonNull
    public TaskSchedulerType getEventDeduplicatorSchedulerType() {
        return TaskSchedulerType.valueOf(eventDeduplicatorSchedulerType);
    }

    /**
     * Get the event signature validator scheduler type
     *
     * @return the event signature validator scheduler type
     */
    @NonNull
    public TaskSchedulerType getEventSignatureValidatorSchedulerType() {
        return TaskSchedulerType.valueOf(eventSignatureValidatorSchedulerType);
    }

    /**
     * Get the orphan buffer scheduler type
     *
     * @return the orphan buffer scheduler type
     */
    @NonNull
    public TaskSchedulerType getOrphanBufferSchedulerType() {
        return TaskSchedulerType.valueOf(orphanBufferSchedulerType);
    }

    /**
     * Get the in-order linker scheduler type
     *
     * @return the in-order linker scheduler type
     */
    @NonNull
    public TaskSchedulerType getInOrderLinkerSchedulerType() {
        return TaskSchedulerType.valueOf(inOrderLinkerSchedulerType);
    }

    /**
     * Get the linked event intake scheduler type
     *
     * @return the linked event intake scheduler type
     */
    @NonNull
    public TaskSchedulerType getLinkedEventIntakeSchedulerType() {
        return TaskSchedulerType.valueOf(linkedEventIntakeSchedulerType);
    }

    /**
     * Get the event creation manager scheduler type
     *
     * @return the event creation manager scheduler type
     */
    @NonNull
    public TaskSchedulerType getEventCreationManagerSchedulerType() {
        return TaskSchedulerType.valueOf(eventCreationManagerSchedulerType);
    }

    /**
     * Get the signed state file manager scheduler type
     *
     * @return the signed state file manager scheduler type
     */
    @NonNull
    public TaskSchedulerType getSignedStateFileManagerSchedulerType() {
        return TaskSchedulerType.valueOf(signedStateFileManagerSchedulerType);
    }

    /**
     * Get the preconsensus event writer scheduler type
     *
     * @return the preconsensus event writer scheduler type
     */
    public TaskSchedulerType getPreconsensusEventWriterSchedulerType() {
        return TaskSchedulerType.valueOf(pcesWriterSchedulerType);
    }
}
