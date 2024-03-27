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

package com.hedera.node.blocknode.filesystem.s3.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.filesystem.s3.S3FileSystem;
import org.junit.jupiter.api.Test;

class S3FileSystemTest {

    @Test
    void localFileSystemNullCheck() {
        final S3FileSystem s3FileSystem = null;
        assertNull(s3FileSystem);
    }

    @Test
    void localFileSystemDoSomethingCheck() {
        final S3FileSystem s3FileSystem = new S3FileSystem(new ConfigProvider());
        assertNotNull(s3FileSystem);
    }
}
