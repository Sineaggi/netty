/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.channels.ClosedChannelException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ChannelInitializerTest") // Run tests one at a time because the server address is shared.
public class ChannelInitializerTest {
    private static final int TIMEOUT_MILLIS = 1000;
    private static final LocalAddress SERVER_ADDRESS = new LocalAddress(ChannelInitializerTest.class);
    private EventLoopGroup group;
    private ServerBootstrap server;
    private Bootstrap client;
    private InspectableHandler testHandler;

    @BeforeEach
    public void setUp() {
        group = new MultithreadEventLoopGroup(1, LocalIoHandler.newFactory());
        server = new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .localAddress(SERVER_ADDRESS);
        client = new Bootstrap()
                .group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelHandler() { });
        testHandler = new InspectableHandler();
    }

    @AfterEach
    public void tearDown() throws Exception {
        group.shutdownGracefully(0, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).asStage().sync();
    }

    @Test
    public void testInitChannelThrowsRegisterFirst() throws Exception {
        testInitChannelThrows(true);
    }

    @Test
    public void testInitChannelThrowsRegisterAfter() throws Exception {
        testInitChannelThrows(false);
    }

    private void testInitChannelThrows(boolean registerFirst) throws Exception {
        final Exception exception = new Exception();
        final AtomicReference<Throwable> causeRef = new AtomicReference<>();

        ChannelPipeline pipeline = new LocalChannel(group.next()).pipeline();

        if (registerFirst) {
            pipeline.channel().register().asStage().sync();
        }
        pipeline.addFirst(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                throw exception;
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                causeRef.set(cause);
                super.channelExceptionCaught(ctx, cause);
            }
        });

        if (!registerFirst) {
            Throwable cause = pipeline.channel().register().asStage().getCause();
            assertThat(cause).isInstanceOf(ClosedChannelException.class);
        }
        pipeline.channel().close().asStage().sync();
        pipeline.channel().closeFuture().asStage().sync();

        assertSame(exception, causeRef.get());
    }

    @Test
    public void testChannelInitializerInInitializerCorrectOrdering() throws Exception {
        final ChannelHandler handler1 = new ChannelHandler() { };
        final ChannelHandler handler2 = new ChannelHandler() { };
        final ChannelHandler handler3 = new ChannelHandler() { };
        final ChannelHandler handler4 = new ChannelHandler() { };

        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(handler1);
                ch.pipeline().addLast(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(handler2);
                        ch.pipeline().addLast(handler3);
                    }
                });
                ch.pipeline().addLast(handler4);
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().asStage().get();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.executor().submit(() -> {
                    // NOOP
                }).asStage().sync();
            Iterator<Map.Entry<String, ChannelHandler>> handlers = channel.pipeline().iterator();
            assertSame(handler1, handlers.next().getValue());
            assertSame(handler2, handlers.next().getValue());
            assertSame(handler3, handlers.next().getValue());
            assertSame(handler4, handlers.next().getValue());
            assertFalse(handlers.hasNext());
        } finally {
            channel.close().asStage().sync();
        }
    }

    @Test
    public void testChannelInitializerReentrance() throws Exception {
        final AtomicInteger registeredCalled = new AtomicInteger(0);
        final ChannelHandler handler1 = new ChannelHandler() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                registeredCalled.incrementAndGet();
            }
        };
        final AtomicInteger initChannelCalled = new AtomicInteger(0);
        client.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                initChannelCalled.incrementAndGet();
                ch.pipeline().addLast(handler1);
                ch.pipeline().fireChannelRegistered();
            }
        }).localAddress(LocalAddress.ANY);

        Channel channel = client.bind().asStage().get();
        try {
            // Execute some task on the EventLoop and wait until its done to be sure all handlers are added to the
            // pipeline.
            channel.executor().submit(() -> {
                    // NOOP
                }).asStage().sync();
            assertEquals(1, initChannelCalled.get());
            assertEquals(2, registeredCalled.get());
        } finally {
            channel.close().asStage().sync();
        }
    }

    @Test
    @Timeout(value = TIMEOUT_MILLIS, unit = TimeUnit.MILLISECONDS)
    public void firstHandlerInPipelineShouldReceiveChannelRegisteredEvent() throws Exception {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addFirst(testHandler);
            }
        });
    }

    @Test
    @Timeout(value = TIMEOUT_MILLIS, unit = TimeUnit.MILLISECONDS)
    public void lastHandlerInPipelineShouldReceiveChannelRegisteredEvent() throws Exception {
        testChannelRegisteredEventPropagation(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel channel) {
                channel.pipeline().addLast(testHandler);
            }
        });
    }

    @Test
    public void testAddFirstChannelInitializer() {
        testAddChannelInitializer(true);
    }

    @Test
    public void testAddLastChannelInitializer() {
        testAddChannelInitializer(false);
    }

    private static void testAddChannelInitializer(final boolean first) {
        final AtomicBoolean called = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelHandler handler = new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        called.set(true);
                    }
                };
                if (first) {
                    ch.pipeline().addFirst(handler);
                } else {
                    ch.pipeline().addLast(handler);
                }
            }
        });
        channel.finish();
        assertTrue(called.get());
    }

    private void testChannelRegisteredEventPropagation(ChannelInitializer<LocalChannel> init) throws Exception {
        Channel clientChannel = null, serverChannel = null;
        try {
            server.childHandler(init);
            serverChannel = server.bind().asStage().get();
            clientChannel = client.connect(SERVER_ADDRESS).asStage().get();
            assertEquals(1, testHandler.channelRegisteredCount.get());
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
        }
    }

    private static void closeChannel(Channel c) throws Exception {
        if (c != null) {
            c.close().asStage().sync();
        }
    }

    private static final class InspectableHandler implements ChannelHandler {
        final AtomicInteger channelRegisteredCount = new AtomicInteger(0);

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            channelRegisteredCount.incrementAndGet();
            ctx.fireChannelRegistered();
        }
    }
}
