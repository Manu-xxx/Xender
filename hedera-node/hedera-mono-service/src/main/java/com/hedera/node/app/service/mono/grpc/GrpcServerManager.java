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

package com.hedera.node.app.service.mono.grpc;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.helidon.grpc.server.GrpcServer;
import java.util.List;
import java.util.function.Consumer;

/** Defines a type able to configure and start the gRPC servers. */
public interface GrpcServerManager {
    @NonNull
    List<GrpcServer> start(int port, int tlsPort, Consumer<String> println);
}
