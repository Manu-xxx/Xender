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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.WritableUpgradeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableUpgradeStoreTest extends FileTestBase {
    private File file;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableUpgradeStore(null));
        assertThrows(NullPointerException.class, () -> writableUpgradeStore.add(null));
    }

    @Test
    void constructorCreatesUpgradeFileState() {
        final var store = new WritableUpgradeStore(filteredWritableStates);
        assertNotNull(store);
    }

    @Test
    void commitsUpgradeFileExist() {
        file = createUpgradeFile();
        assertFalse(writableUpgradeStates.iterator().hasNext());

        writableUpgradeStates.add(file.contents());
        writableUpgradeFileStates.put(file.fileId(), file);

        assertTrue(writableUpgradeFileStates.get(fileUpgradeFileId).fileId().fileNum() == 150L);
        final var writtenFile = writableUpgradeFileStates.get(fileUpgradeFileId);
        assertEquals(file, writtenFile);
        assertEquals(file.contents(), writableUpgradeStates.peek());
    }

    @Test
    void getReturnsUpgradeFile() {
        file = createUpgradeFile();
        writableUpgradeStates.add(file.contents());
        writableUpgradeFileStates.put(file.fileId(), file);

        final var readFile = writableUpgradeStates.peek();

        assertEquals(file.contents(), readFile);
        assertEquals(file, writableUpgradeFileStates.get(fileUpgradeFileId));
    }
}
