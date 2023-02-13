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
package com.hedera.node.app.service.consensus.impl.components;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.properties.PropertiesModule;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = PropertiesModule.class)
public interface ConsensusComponent {
    @Component.Factory
    interface Factory {
        ConsensusComponent create(@BindsInstance @BootstrapProps PropertySource bootstrapProperties);
    }

    ConsensusCreateTopicHandler consensusCreateTopicHandler();

    ConsensusDeleteTopicHandler consensusDeleteTopicHandler();

    ConsensusGetTopicInfoHandler consensusGetTopicInfoHandler();

    ConsensusSubmitMessageHandler consensusSubmitMessageHandler();

    ConsensusUpdateTopicHandler consensusUpdateTopicHandler();
}
