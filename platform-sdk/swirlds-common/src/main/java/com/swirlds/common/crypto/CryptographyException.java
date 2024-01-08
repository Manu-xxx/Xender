/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import com.swirlds.common.exceptions.PlatformException;
import com.swirlds.logging.legacy.LogMarker;

public class CryptographyException extends PlatformException {
    private static final LogMarker DEFAULT_MARKER = LogMarker.EXCEPTION;

    public CryptographyException(final LogMarker logMarker) {
        super(logMarker);
    }

    public CryptographyException(final String message, final LogMarker logMarker) {
        super(message, logMarker);
    }

    public CryptographyException(final String message, final Throwable cause, final LogMarker logMarker) {
        super(message, cause, logMarker);
    }

    public CryptographyException(final Throwable cause, final LogMarker logMarker) {
        super(cause, logMarker);
    }

    public CryptographyException(final Throwable cause) {
        super(cause, DEFAULT_MARKER);
    }
}
