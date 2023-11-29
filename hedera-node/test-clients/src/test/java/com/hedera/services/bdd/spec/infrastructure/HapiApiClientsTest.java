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

package com.hedera.services.bdd.spec.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.infrastructure.interceptor.RetryCallbacks;
import com.hedera.services.bdd.spec.infrastructure.interceptor.RetryInterceptor;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.*;
import io.grpc.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

public class HapiApiClientsTest {

    private static HapiApiClients apiClients;
    private static AccountID accountID;
    private static NodeConnectInfo node;

    private static Channel serverChannel;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static ExecutorService serverExecutor;
    private static CountDownLatch latch;
    private static AtomicInteger serverHandlerCalls;

    @BeforeEach
    public void startServer() throws InterruptedException {
        latch = new CountDownLatch(1);
        int port = 50211;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        serverHandlerCalls = new AtomicInteger();

        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new Http2ServerInitializer());

                serverChannel = b.bind(port)
                        .addListener(future -> {
                            if (future.isSuccess()) {
                                System.out.println("Server successfully started.");
                                latch.countDown();
                            } else {
                                System.err.println("Server failed to bind to port: " + port);
                            }
                        })
                        .syncUninterruptibly()
                        .channel();
                System.out.println("HTTP/2 (no SSL) server started at port " + port);
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });

        // Wait for the server to be up before proceeding
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Server did not start within the expected time");
        }
    }

    @AfterEach
    public void stopServer() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
    }

    @BeforeAll
    public static void setup() {
        // Initialize HapiApiClients with mock nodes or test configuration
        HapiSpecSetup setup = createTestSetup();
        node = setup.nodes().get(0);
        accountID = node.getAccount();
        System.out.printf("node: %s | accountID: %s\n", node.uri(), accountID);
        apiClients = HapiApiClients.clientsFor(setup);
    }

    /**
     * This test simulates the GOAWAY from the server by closing the channel.
     */
    @Test
    @Disabled
    public void testConnectionGoawayWithStub() {
        // Get a stub to simulate GOAWAY
        ManagedChannel channel = getChannelToTerminate(apiClients);
        simulateGoAwaySignal(channel);

        // Attempt to use a stub from the terminated channel
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query =
                    Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var response = apiClients.getCryptoSvcStub(accountID, false).cryptoGetBalance(query);
            fail("Expected an exception due to GOAWAY signal but none was thrown.");
        } catch (StatusRuntimeException e) {
            // Assert that the correct exception is thrown
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we get an exception when there are no retries enabled.
     */
    @Test
    @Disabled
    public void testConnectionGoawayNoInterceptor() {
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var client = apiClients.getCryptoSvcStub(accountID, false);
            final var response = client.cryptoGetBalance(query);
            fail(String.format("Expected an exception due to GOAWAY signal but none was thrown: %s", response.toString()));
        } catch (StatusRuntimeException e) {
            // Assert that the correct exception is thrown.
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we get an exception when there is a RetryInterceptor set but no are no retries enabled.
     */
    @Test
    public void testConnectionGoawayWithRetryInterceptorAndZeroRetries() throws InterruptedException {
        // Some retry setup. In practice only the RetryInterceptor and setRetries is needed.
        // The setRetryCallbacks and CountDownLatch are only so we can verify the retry happened.
        var retries = 0;
        var latch = new CountDownLatch(retries);
        var retryCallbacks = new RetryCallbacksLatch(latch);
        var interceptor = RetryInterceptor.newBuilder()
                .setRetries(retries) // Set the number of times this interceptor is allowed to retry.
                .setRetryCallbacks(retryCallbacks); // On each retry, execute our callback, so we can verify it retried.

        apiClients.setClientInterceptor(interceptor);

        final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
        final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
        final var client = apiClients.getCryptoSvcStub(accountID, false);

        // Execute the query.
        var response = client.cryptoGetBalance(query);

        // Assert that the query did not fail.
        assertNotNull(response, "Could not get response");
        assertEquals(response.getCryptogetAccountBalance().getBalance(), 1000);

        // We now need to assert that the latch was counted down. We should have had zero retries.
        // The latch should execute immediately, leaving this here so this test doesn't hang if there
        // is a problem in the future.
        boolean called = latch.await(5, TimeUnit.SECONDS);

        // Test should fail if the callback is not called within the timeout
        assertTrue(called, "RetryCallback was not called within the timeout");
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we pass when there are retries enabled.
     */
    @Test
    public void testConnectionGoawayWithRetryInterceptorAndOneRetries() throws InterruptedException {
        // Some retry setup. In practice only the RetryInterceptor and setRetries is needed.
        // The setRetryCallbacks and CountDownLatch are only so we can verify the retry happened.
        var retries = 1;
        var latch = new CountDownLatch(retries);
        var retryCallbacks = new RetryCallbacksLatch(latch);
        var interceptor = RetryInterceptor.newBuilder()
                .setRetries(retries) // Set the number of times this interceptor is allowed to retry.
                .setRetryCallbacks(retryCallbacks); // On each retry, execute our callback, so we can verify it retried.

        apiClients.setClientInterceptor(interceptor);

        final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
        final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
        final var client = apiClients.getCryptoSvcStub(accountID, false);

        System.out.println("using client to get the balance");
        var response = client.cryptoGetBalance(query);

        System.out.printf(
                "in executor - ran crypto get balance %s\n",
                response.getCryptogetAccountBalance().getBalance());

        // We should be getting here because on the second retry it should pass.
//        System.out.printf("ran crypto get balance 2:   %s\n", response.toString());

        // Assert that the response passed this time.
        assertNotNull(response, "Could not get response");
        assertEquals(response.getCryptogetAccountBalance().getBalance(), 1000);

        // We now need to assert that the latch was counted down. We should have had one retry.
        boolean called = latch.await(5, TimeUnit.SECONDS);

        // Test should fail if the callback is not called within the timeout
        assertTrue(called, "RetryCallback was not called within the timeout");
    }

    private static void simulateGoAwaySignal(ManagedChannel channel) {
        // Shutting down the channel to simulate a GOAWAY condition
        channel.shutdown();
        try {
            // Optionally, wait for the channel to terminate to ensure the GOAWAY condition is simulated.
            // I hate waiting like this, but I need to dig in more and create a hook for this.
            boolean terminated = channel.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                System.err.println("Channel did not terminate in the expected time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for channel termination", e);
        }
    }

    private static ManagedChannel getChannelToTerminate(HapiApiClients clients) {
        return clients.getChannel(accountID, false);
    }

    private static HapiSpecSetup createTestSetup() {
        // Create and return a HapiSpecSetup instance with the necessary test configuration
        return new HapiSpecSetup(HapiSpecSetup.getDefaultPropertySource());
    }

    @AfterAll
    public static void tearDown() {
        // Clean up resources
        HapiApiClients.tearDown();
    }

    private static class Http2ServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new LoggingHandler());
            p.addLast(Http2FrameCodecBuilder.forServer().build());
            p.addLast(new Http2ServerHandler());
        }
    }

    //    private static class Http2ServerHandler extends SimpleChannelInboundHandler<Http2Frame> {
    //
    //        private Http2ServerHandler() { }
    //
    //        @Override
    //        protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) throws InterruptedException {
    //            System.out.printf("called channelRead0: %s\n", frame);
    //            if (frame instanceof Http2PingFrame) {
    //                Http2PingFrame pingFrame = (Http2PingFrame) frame;
    //                if (!pingFrame.ack()) {
    //                    // Respond with a PING frame with ACK set to true
    //                    ctx.writeAndFlush(new DefaultHttp2PingFrame(pingFrame.content(), true));
    //                }
    //            }
    //            if frame instanceof Http2DataFrame {
    //                DefaultHttp2DataFrame dataFrame = (DefaultHttp2DataFrame) frame;
    //                if (dataFrame.isEndStream()) {
    //
    //                }
    //            }
    //            if (frame instanceof Http2HeadersFrame) {
    //                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
    //
    //                // Send a Headers Frame as a response
    //                Http2Headers responseHeaders = new DefaultHttp2Headers();
    //                responseHeaders.status("200"); // OK Status
    //                responseHeaders.add("content-type", "application/grpc");
    //
    //                // Send a Data Frame with some content
    //                ByteBuf content = ctx.alloc().buffer();
    //                long balance = 1000; // Mock balance
    //                var response = CryptoGetAccountBalanceResponse.newBuilder()
    //
    // .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
    //                        .setAccountID(accountID)
    //                        .setBalance(balance)
    //                        .build();
    //                byte[] responseBytes = response.toByteArray(); // Serialize the response
    //                // Write the gRPC frame header
    //                content.writeByte(0); // Compression flag (0 for uncompressed)
    //                content.writeInt(responseBytes.length); // Message length
    //                content.writeBytes(responseBytes); // Write the serialized bytes to the buffer
    //
    //                // Send headers frame
    //                ctx.write(new DefaultHttp2HeadersFrame(responseHeaders).stream(headersFrame.stream()));
    //
    //                // If this is the first request, write the GOAWAY.
    //                final var call = serverHandlerCalls.getAndIncrement();
    //                if (call < 1) {
    //                    System.out.printf("sending GOAWAY: %d\n", call);
    //                    // Send data frame without end of stream flag
    ////                    ctx.writeAndFlush(new DefaultHttp2DataFrame(content,
    // false).stream(headersFrame.stream())).addListener(futr -> {
    ////                        if (futr.isSuccess()) {
    ////                            System.out.println("DataFrame 1 success");
    //                            // Send data frame with end of stream flag
    ////                            ctx.writeAndFlush(new
    // DefaultHttp2DataFrame(true).stream(headersFrame.stream())).addListener(futr2 -> {
    ////                                if (futr2.isSuccess()) {
    ////                                    System.out.println("DataFrame 2 success");
    //                                    // Ensure all response frames are flushed before sending GOAWAY
    //                                    ctx.writeAndFlush(new
    // DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR)).addListener(future -> {
    //                                        if (future.isSuccess()) {
    //                                            System.out.println("closing ctx 1");
    //                                           //  ctx.close();
    //                                        } else {
    //                                            System.out.println("not success future");
    //                                        }
    //                                    });
    ////                                } else {
    ////                                    System.out.println("futr2 not success future");
    ////                                }
    ////                            });
    ////                        } else {
    ////                            System.out.println("not success futr");
    ////                        }
    ////                    });
    //
    //                } else {
    //                    System.out.printf("NOT sending GOAWAY: %d\n", call);
    //                    // Send data frame with end of stream flag
    //                    ctx.writeAndFlush(new DefaultHttp2DataFrame(content,
    // true).stream(headersFrame.stream())).addListener(future -> {
    //                        // Close the channel after ensuring the data frame is flushed
    //                        if (future.isSuccess()) {
    //                            // System.out.println("closing ctx 2");
    //                           // ctx.close();
    //                        } else {
    //                            System.out.println("NOTGOAWAY: not success future");
    //                        }
    //                    });
    //                }
    //            }
    //        }
    //    }

    private static class Http2ServerHandler extends SimpleChannelInboundHandler<Http2Frame> {

        private ByteBuf accumulatedData;
        private boolean endOfStreamReceived = false;

        public Http2ServerHandler() {
            accumulatedData = Unpooled.buffer();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) {
            System.out.printf("called channelRead0: %s\n", frame);

            Http2FrameStream http2Stream = null;

            if (frame instanceof DefaultHttp2PingFrame) {
                DefaultHttp2PingFrame pingFrame = (DefaultHttp2PingFrame) frame;
                if (!pingFrame.ack()) {
                    System.out.println("sending PING ACK");
                    // Echo the ping frame with ACK set
                    ctx.write(new DefaultHttp2PingFrame(pingFrame.content(), true));
                }
            }

            if (frame instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                accumulatedData.writeBytes(dataFrame.content());
                endOfStreamReceived = dataFrame.isEndStream();
                http2Stream = dataFrame.stream();
            }

            if (endOfStreamReceived && http2Stream != null) {
                processRequestAndSendResponse(ctx, http2Stream);
                accumulatedData.clear();
                endOfStreamReceived = false;
            }
        }

        /**
         * This method crafts a test response to the client.
         *
         * @see io.grpc.internal.Http2ClientStreamTransportState#transportDataReceived for details on the response protocol.
         */
        private void processRequestAndSendResponse(ChannelHandlerContext ctx, Http2FrameStream stream)  {
            System.out.printf("server got end of stream... processing dataframe request: %s\n", accumulatedData.toString());
            // Assuming accumulatedData contains the serialized request

            // Create and serialize the response
            CryptoGetAccountBalanceResponse.Builder b = CryptoGetAccountBalanceResponse.newBuilder()
//                    .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
                    .setAccountID(accountID)
                    .setBalance(1000L); // Mock balance
            Response response = Response.newBuilder().setCryptogetAccountBalance(b).build();

            byte[] responseBytes = response.toByteArray(); // new byte[]{};

            System.out.printf("responseBytes: %s\n", Arrays.toString(responseBytes));
            System.out.printf("responseBytes len: %d\n", responseBytes.length);


            ByteBuf content = ctx.alloc().buffer();
            content.writeByte(0); // Compression flag
            content.writeInt(responseBytes.length); // Message length
            content.writeBytes(responseBytes);

            System.out.printf("content: %s\n", Arrays.toString(ByteBufUtil.getBytes(content)));

            // Send the response
            Http2Headers responseHeaders = new DefaultHttp2Headers();
            responseHeaders.status("200"); // OK Status
            responseHeaders.add("content-type", "application/grpc");
            ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(stream));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(content, false).stream(stream));

            // Finally, send the trailers (with EOS flag).
            // According to Http2ClientStreamTransportState.transportDataReceived
            // you cannot end a stream with a DefaultHttp2DataFrame, so we must do this with a trailer.
            Http2Headers trailers = new DefaultHttp2Headers();
            trailers.set("content-length", String.format("%d", responseBytes.length));
            trailers.add("grpc-status", "0"); // "0" indicates success
            trailers.add("grpc-message", ""); // Optional message, can be left empty for success
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true).stream(stream));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            accumulatedData.release();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    private static class LoggingHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            System.out.println("Connection established with: " + remoteAddress);
            super.channelActive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Exception caught: " + cause.getMessage());
            ctx.close();
        }
    }

    private static class RetryCallbacksLatch implements RetryCallbacks {

        private final CountDownLatch latch;

        private RetryCallbacksLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public <ReqT, RespT> boolean onRetry(
                io.grpc.MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                io.grpc.Channel newChannel,
                ClientCall.Listener<RespT> originalListener,
                Metadata originalHeaders) {
            this.latch.countDown();
            return true;
        }
    }
}
