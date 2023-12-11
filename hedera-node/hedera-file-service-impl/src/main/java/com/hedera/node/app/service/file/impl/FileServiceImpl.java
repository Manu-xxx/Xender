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

package com.hedera.node.app.service.file.impl;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.FileGenesisSchema;
import com.hedera.node.app.service.file.impl.codec.FileServiceStateTranslator;
import com.hedera.node.app.service.mono.files.DataMapFactory;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.files.store.FcBlobsBytesStore;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.swirlds.common.threading.manager.AdHocThreadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import edu.umd.cs.findbugs.annotations.NonNull;

/** Standard implementation of the {@link FileService} {@link com.hedera.node.app.spi.Service}. */
public final class FileServiceImpl implements FileService {
    public static final String BLOBS_KEY = "FILES";
    public static final String UPGRADE_FILE_KEY = "UPGRADE_FILE";
    public static final String UPGRADE_DATA_KEY = "UPGRADE_DATA";

    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss;
    private Map<com.hederahashgraph.api.proto.java.FileID, byte[]> fileContents;
    private Map<com.hederahashgraph.api.proto.java.FileID, HFileMeta> fileAttrs;

    public void setFs(Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        this.fss = fss;
        var blobStore = new FcBlobsBytesStore(fss);
        this.fileContents = DataMapFactory.dataMapFrom(blobStore);
        this.fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new FileGenesisSchema());

//        if(true)return;
        // BBM: reducing version just for testing
        registry.register(new Schema(SemanticVersion.newBuilder().minor(44).build()) {
            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                var ts = ctx.newStates().<FileID, File>get(BLOBS_KEY);

                System.out.println("BBM:running file migration...");
                List<com.hederahashgraph.api.proto.java.FileID> fileIds = new ArrayList<>();
                try {
                    fss.get().extractVirtualMapData(AdHocThreadManager.getStaticThreadManager(), entry -> {
                        fileIds.add(com.hederahashgraph.api.proto.java.FileID.newBuilder().setFileNum(entry.left().getEntityNumCode()).build());
                    }, 1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                fileIds.forEach(fromFileId -> {
                    var fromFileMeta = fileAttrs.get(fromFileId);
                    if (fromFileMeta != null) {
                        var toFile = FileServiceStateTranslator.stateToPbj(
                                fileContents.get(fromFileId),
                                fromFileMeta, fromFileId);
                        ts.put(FileID.newBuilder().fileNum(fromFileId.getFileNum()).build(),
                                toFile);
                    } else {
                        System.out.println("BBM: WARN: no meta for fileid: " + fromFileId);
                    }
                });

                if (ts.isModified()) ((WritableKVStateBase) ts).commit();

                fss = null;
                fileContents = null;
                fileAttrs = null;

                System.out.println("BBM:finished file migration");
            }
        });
    }
}
