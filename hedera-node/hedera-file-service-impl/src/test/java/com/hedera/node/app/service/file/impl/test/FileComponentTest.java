/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.file.impl.components.DaggerFileComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import org.junit.jupiter.api.Test;

class FileComponentTest {
    @Test
    void objectGraphRootsAreAvailable() {
        // given:
        FileComponent subject = DaggerFileComponent.factory().create();

        // expect:
        assertNotNull(subject.fileAppendHandler());
        assertNotNull(subject.fileCreateHandler());
        assertNotNull(subject.fileDeleteHandler());
        assertNotNull(subject.fileUpdateHandler());
        assertNotNull(subject.fileGetContentsHandler());
        assertNotNull(subject.fileGetInfoHandler());
        assertNotNull(subject.fileSystemDeleteHandler());
        assertNotNull(subject.fileSystemUndeleteHandler());
    }
}
