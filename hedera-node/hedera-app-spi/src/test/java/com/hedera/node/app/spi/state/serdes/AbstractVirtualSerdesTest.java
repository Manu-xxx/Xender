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
package com.hedera.node.app.spi.state.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

abstract class AbstractVirtualSerdesTest<T> {
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final String RANDOM_INSTANCES = "randomInstances";
    protected final Serdes<T> subject;
    public static final int MAX_SUPPORTED_SERIALIZED_SIZE = 4096;

    protected AbstractVirtualSerdesTest(final Serdes<T> subject) {
        this.subject = subject;
    }

    @ParameterizedTest
    @ArgumentsSource(InstanceArgumentsProvider.class)
    void canWriteAndParse(final T instance) {
        final T deserialized;
        if (RANDOM.nextBoolean()) {
            final var serialized = writeUsingStream(instance);
            deserialized = parseUsingStream(serialized);
        } else {
            final var serialized = writeUsingBuffer(instance);
            deserialized = parseUsingBuffer(serialized);
        }
        assertEquals(instance, deserialized);
    }

    protected static class InstanceArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context)
                throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            final var testType = context.getRequiredTestClass();
            final var randomInstances = testType.getMethod(RANDOM_INSTANCES);
            return ((Stream<?>) randomInstances.invoke(null)).map(Arguments::of);
        }
    }

    protected byte[] writeUsingStream(final T instance) {
        final var baos = new ByteArrayOutputStream();
        final var out = new SerializableDataOutputStream(baos);
        try {
            subject.write(instance, out);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    protected byte[] writeUsingBuffer(final T instance) {
        final var buffer = ByteBuffer.allocate(MAX_SUPPORTED_SERIALIZED_SIZE);
        final var bb = new ByteBufferDataOutput(buffer);
        try {
            subject.write(instance, bb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var pos = buffer.position();
        return Arrays.copyOfRange(buffer.array(), 0, pos);
    }

    private T parseUsingStream(final byte[] serialized) {
        final T instance;
        final var bais = new ByteArrayInputStream(serialized);
        final var in = new SerializableDataInputStream(bais);
        byte[] leftover;
        try {
            instance = subject.parse(in);
            leftover = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(0, leftover.length, "No bytes should be left in the stream");
        return instance;
    }

    private T parseUsingBuffer(final byte[] serialized) {
        final T instance;
        final var buffer = ByteBuffer.wrap(serialized);
        final var bb = new ByteBufferDataInput(buffer);
        try {
            instance = subject.parse(bb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            buffer.get();
            Assertions.fail("No bytes should be left in the buffer");
        } catch (BufferUnderflowException ignore) {
        }
        return instance;
    }
}
