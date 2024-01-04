/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.locks;

import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeUnit;

/**
 * A {@link AutoClosableLock} that can lock a resource.
 *
 * @param <T>
 * 		type of the resource
 */
public interface AutoClosableResourceLock<T> extends AutoClosableLock {

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    LockedResource<T> lock();

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    LockedResource<T> lockInterruptibly() throws InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    MaybeLockedResource<T> tryLock();

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    MaybeLockedResource<T> tryLock(final long time, @NonNull final TimeUnit unit) throws InterruptedException;
}
