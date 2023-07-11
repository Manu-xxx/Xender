/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.grpc;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.MethodDescriptor;
//import io.helidon.grpc.core.MarshallerSupplier;
//import io.helidon.grpc.server.ServiceDescriptor;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenient builder API for constructing gRPC Service definitions. The {@link GrpcServiceBuilder}
 * is capable of constructing service definitions for {@link Transaction} based calls using the
 * {@link #transaction(String)} method, or {@link Query} based calls using the {@link
 * #query(String)} method.
 *
 * <p>Every gRPC service definition needs to define, per service method definition, the "marshaller"
 * to use for marshalling and unmarshalling binary data sent in the protocol. Usually this is some
 * kind of protobuf parser. In our case, we simply read a byte array from the {@link InputStream}
 * and pass the raw array to the appropriate workflow implementation {@link IngestWorkflow} or
 * {@link QueryWorkflow}, so they can do the protobuf parsing. We do this to segregate the code.
 * This class is <strong>only</strong> responsible for the gRPC call, the workflows are responsible
 * for working with protobuf.
 */
/*@NotThreadSafe*/
public final class GrpcServiceBuilder {
    /** Logger */
    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceBuilder.class);

    /**
     * Create a single JVM-wide Marshaller instance that simply reads/writes byte arrays to/from
     * {@link InputStream}s. This class is thread safe.
     */
    private static final DataBufferMarshaller MARSHALLER = new DataBufferMarshaller();

    /**
     * Create a single instance of the marshaller supplier to provide to every gRPC method
     * registered with the system. We only need the one, and it always returns the same
     * NoopMarshaller instance. This is fine to use with multiple app instances within the same JVM.
     */
//    private static final MarshallerSupplier MARSHALLER_SUPPLIER = new MarshallerSupplier() {
//        @Override
//        public <T> MethodDescriptor.Marshaller<T> get(final Class<T> clazz) {
//            //noinspection unchecked
//            return (MethodDescriptor.Marshaller<T>) MARSHALLER;
//        }
//    };

    /** The name of the service we are building. For example, the TokenService. */
    private final String serviceName;

    /**
     * The {@link IngestWorkflow} to invoke for transaction methods.
     *
     * <p>This instance is set in the constructor and reused for all transaction and query handlers defined
     * on this service builder.
     */
    private final IngestWorkflow ingestWorkflow;

    /**
     * The {@link QueryWorkflow} to invoke for query methods.
     *
     * <p>This instance is set in the constructor and reused for all transaction and query handlers defined
     * on this service builder.
     */
    private final QueryWorkflow queryWorkflow;

    /**
     * The set of transaction method names that need corresponding service method definitions generated.
     *
     * <p>Initially this set is empty, and is populated by calls to {@link #transaction(String)}. Then,
     * when {@link #build(Metrics)} is called, the set is used to create the transaction service method definitions.
     */
    private final Set<String> txMethodNames = new HashSet<>();

    /**
     * The set of query method names that need corresponding service method definitions generated.
     *
     * <p>Initially this set is empty, and is populated by calls to {@link #query(String)}. Then,
     * when {@link #build(Metrics)} is called, the set is used to create the query service method definitions.
     */
    private final Set<String> queryMethodNames = new HashSet<>();

    /**
     * Creates a new builder. Typically only a single builder instance is created per service.
     *
     * @param serviceName The name of the service. Cannot be null or blank.
     * @param ingestWorkflow The workflow to use for handling all transaction ingestion API calls
     * @param queryWorkflow The workflow to use for handling all queries
     * @throws NullPointerException if any of the parameters are null
     * @throws IllegalArgumentException if the serviceName is blank
     */
    public GrpcServiceBuilder(
            @NonNull final String serviceName,
            @NonNull final IngestWorkflow ingestWorkflow,
            @NonNull final QueryWorkflow queryWorkflow) {
        this.ingestWorkflow = requireNonNull(ingestWorkflow);
        this.queryWorkflow = requireNonNull(queryWorkflow);
        this.serviceName = requireNonNull(serviceName);
        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName cannot be blank");
        }
    }

    /**
     * Register the creation of a new gRPC method for handling transactions with the given name.
     * This call is idempotent.
     *
     * @param methodName The name of the transaction method. Cannot be null or blank.
     * @return A reference to the builder.
     * @throws NullPointerException if the methodName is null
     * @throws IllegalArgumentException if the methodName is blank
     */
    public @NonNull GrpcServiceBuilder transaction(@NonNull final String methodName) {
        if (requireNonNull(methodName).isBlank()) {
            throw new IllegalArgumentException("The gRPC method name cannot be blank");
        }

        txMethodNames.add(methodName);
        return this;
    }

    /**
     * Register the creation of a new gRPC method for handling queries with the given name. This
     * call is idempotent.
     *
     * @param methodName The name of the query method. Cannot be null or blank.
     * @return A reference to the builder.
     * @throws NullPointerException if the methodName is null
     * @throws IllegalArgumentException if the methodName is blank
     */
    public @NonNull GrpcServiceBuilder query(@NonNull final String methodName) {
        if (requireNonNull(methodName).isBlank()) {
            throw new IllegalArgumentException("The gRPC method name cannot be blank");
        }

        queryMethodNames.add(methodName);
        return this;
    }

    /**
     * Build a gRPC {@link ServiceDescriptor} for each transaction and query method registered with
     * this builder.
     *
     * @return a non-null {@link ServiceDescriptor}.
     */
    public Object build(final Metrics metrics) {
//    public ServiceDescriptor build(final Metrics metrics) {
//        final var builder = ServiceDescriptor.builder(null, serviceName);
//        txMethodNames.forEach(methodName -> {
//            logger.debug("Registering gRPC transaction method {}.{}", serviceName, methodName);
//            final var method = new TransactionMethod(serviceName, methodName, ingestWorkflow, metrics);
//            builder.unary(methodName, method, rules -> rules.marshallerSupplier(MARSHALLER_SUPPLIER));
//        });
//        queryMethodNames.forEach(methodName -> {
//            logger.debug("Registering gRPC query method {}.{}", serviceName, methodName);
//            final var method = new QueryMethod(serviceName, methodName, queryWorkflow, metrics);
//            builder.unary(methodName, method, rules -> rules.marshallerSupplier(MARSHALLER_SUPPLIER));
//        });
//        return builder.build();
        return null;
    }
}
