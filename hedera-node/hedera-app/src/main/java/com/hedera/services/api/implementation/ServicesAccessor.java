/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;

/**
 * A {@code ServiceAccessor} is used to pass all services to components via a single parameter.
 *
 * @param cryptoService a {@link CryptoService}
 * @param fileService a {@link FileService}
 * @param tokenService a {@link TokenService}
 */
public record ServicesAccessor(
        ServicesContext<CryptoService> cryptoService,
        ServicesContext<FileService> fileService,
        ServicesContext<TokenService> tokenService) {}
