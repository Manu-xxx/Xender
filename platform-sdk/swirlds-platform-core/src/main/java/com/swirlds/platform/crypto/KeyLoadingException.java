/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.crypto;

/**
 * Thrown when an issue occurs while loading keys from pfx files
 */
public class KeyLoadingException extends Exception {
    public KeyLoadingException(final String message) {
        super(message);
    }

    public KeyLoadingException(final String message, final KeyCertPurpose type, final String name) {
        super(message + " Missing:" + type.storeName(name));
    }

    public KeyLoadingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
