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

package com.hedera.node.app.spi.validation;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A utility class, similar in concept {@link java.util.Objects#requireNonNull(Object)}, to validate or throw
 * exceptions.
 */
public final class Validations {

    /** No instantiation permitted */
    private Validations() {}

    /**
     * Checks that the given subject is not null. If it is, then a {@link PreCheckException} is thrown with the
     * given {@link ResponseCodeEnum}.
     *
     * @param subject The object to check.
     * @param code The {@link ResponseCodeEnum} to use if the subject is null.
     * @return The subject if it is not null.
     * @param <T> The type of the subject.
     * @throws PreCheckException If the subject is null, a {@link PreCheckException} is thrown with the given
     * {@link ResponseCodeEnum}.
     */
    public static <T> T mustExist(@Nullable final T subject, @NonNull final ResponseCodeEnum code)
            throws PreCheckException {
        if (subject == null) {
            throw new PreCheckException(code);
        }

        return subject;
    }

    public static void mustNotBeEmpty(@Nullable final List<?>list, @NonNull final ResponseCodeEnum code) throws PreCheckException {
        if (list == null || list.isEmpty()) {
            throw new PreCheckException(code);
        }
    }

    /**
     * Common validation of an {@link AccountID} that it is internally consistent. A valid ID must not be null,
     * must have either an alias or an account number, and if it has an account number, it must be positive. And
     * if there is an alias, it must have at least one byte.
     *
     * @param subject The {@link AccountID} to validate.
     * @return The {@link AccountID} if valid.
     * @throws PreCheckException If the account ID is not valid, {@link ResponseCodeEnum#INVALID_ACCOUNT_ID} will
     * be thrown.
     */
    public static AccountID validateAccountID(@Nullable final AccountID subject) throws PreCheckException {
        // Cannot be null
        if (subject == null) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        // Must have either alias or account num
        if (!subject.hasAlias() && !subject.hasAccountNum()) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        // You cannot have negative or zero account numbers. Those just aren't allowed!
        if (subject.hasAccountNum() && subject.accountNumOrThrow() <= 0) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }

        // And if you have an alias, it has to have at least a byte.
        if (subject.hasAlias()) {
            final var alias = subject.aliasOrThrow();
            if (alias.length() < 1) {
                throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
            }
        }

        return subject;
    }
}
