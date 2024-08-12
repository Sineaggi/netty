/*
 * Copyright 2017 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompositeBufferGatheringWriteTest extends AbstractSocketTest {
    private static final int EXPECTED_BYTES = 20;

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testSingleCompositeBufferWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSingleCompositeBufferWrite);
    }

    public void testSingleCompositeBufferWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Object> clientReceived = new AtomicReference<>();
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelHandler() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ctx.writeAndFlush(newCompositeBuffer(ctx.bufferAllocator()))
                                    .addListener(ctx, ChannelFutureListeners.CLOSE);
                        }
                    });
                }
            });
            cb.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelHandler() {
                        private Buffer aggregator;

                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            aggregator = ctx.bufferAllocator().allocate(EXPECTED_BYTES);
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Buffer) {
                                try (Buffer buf = (Buffer) msg) {
                                    aggregator.writeBytes(buf);
                                }
                            }
                        }

                        @Override
                        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            // IOException is fine as it will also close the channel and may just be a connection reset.
                            if (!(cause instanceof IOException)) {
                                closeAggregator();
                                clientReceived.set(cause);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            if (clientReceived.compareAndSet(null, aggregator)) {
                                try {
                                    assertEquals(EXPECTED_BYTES, aggregator.readableBytes());
                                } catch (Throwable cause) {
                                    closeAggregator();
                                    clientReceived.set(cause);
                                } finally {
                                    latch.countDown();
                                }
                            }
                        }

                        private void closeAggregator() {
                            if (aggregator != null) {
                                aggregator.close();
                                aggregator = null;
                            }
                        }
                    });
                }
            });

            serverChannel = sb.bind().asStage().get();
            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();

            try (Buffer expected = newCompositeBuffer(clientChannel.bufferAllocator())) {
                latch.await();
                Object received = clientReceived.get();
                if (received instanceof Buffer) {
                    try (Buffer actual = (Buffer) received) {
                        assertEquals(expected, actual);
                    }
                } else {
                    throw (Throwable) received;
                }
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testCompositeBufferPartialWriteDoesNotCorruptData(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testCompositeBufferPartialWriteDoesNotCorruptData);
    }

    public void testCompositeBufferPartialWriteDoesNotCorruptData(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        // The scenario is the following:
        // Limit SO_SNDBUF so that a single buffer can be written, and part of a CompositeBuffer at the same time.
        // We then write the single buffer, the CompositeBuffer, and another single buffer and verify the data is not
        // corrupted when we read it on the other side.
        Channel serverChannel = null;
        Channel clientChannel = null;
        BufferAllocator alloc = DefaultBufferAllocators.preferredAllocator();
        final int soSndBuf = 1024;
        try (Buffer expectedContent = alloc.allocate(soSndBuf * 2)) {
            Random r = new Random();
            expectedContent.writeBytes(newRandomBytes(expectedContent.writableBytes(), r));
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Object> clientReceived = new AtomicReference<>();
            sb.childOption(ChannelOption.SO_SNDBUF, soSndBuf)
              .childHandler(new ChannelInitializer<>() {
                  @Override
                  protected void initChannel(Channel ch) throws Exception {
                      ch.pipeline().addLast(new ChannelHandler() {
                          @Override
                          public void channelActive(ChannelHandlerContext ctx) throws Exception {
                              compositeBufferPartialWriteDoesNotCorruptDataInitServerConfig(
                                      ctx.channel(), soSndBuf);
                              Buffer contents = expectedContent.copy();
                              // First single write
                              ctx.write(contents.readSplit(soSndBuf - 100));

                              // Build and write CompositeBuffer
                              CompositeBuffer compositeBuffer = ctx.bufferAllocator().compose(asList(
                                      contents.readSplit(50).send(),
                                      contents.readSplit(200).send()));
                              ctx.write(compositeBuffer);

                              // Write a single buffer that is smaller than the second component of the
                              // CompositeBuffer above but small enough to fit in the remaining space allowed by the
                              // soSndBuf amount.
                              ctx.write(contents.readSplit(50));

                              // Write the remainder of the content
                              ctx.writeAndFlush(contents).addListener(ctx, ChannelFutureListeners.CLOSE);
                          }

                          @Override
                          public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                              // IOException is fine as it will also close the channel
                              // and may just be a connection reset.
                              if (!(cause instanceof IOException)) {
                                  clientReceived.set(cause);
                                  latch.countDown();
                              }
                          }
                      });
                  }
              });
            cb.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelHandler() {
                        private Buffer aggregator;

                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            aggregator = ctx.bufferAllocator().allocate(expectedContent.readableBytes());
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Buffer) {
                                try (Buffer buf = (Buffer) msg) {
                                    aggregator.writeBytes(buf);
                                }
                            }
                        }

                        @Override
                        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            // IOException is fine as it will also close the channel
                            // and may just be a connection reset.
                            if (!(cause instanceof IOException)) {
                                closeAggregator();
                                clientReceived.set(cause);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            if (clientReceived.compareAndSet(null, aggregator)) {
                                try {
                                    assertEquals(expectedContent.readableBytes(), aggregator.readableBytes());
                                } catch (Throwable cause) {
                                    closeAggregator();
                                    clientReceived.set(cause);
                                } finally {
                                    latch.countDown();
                                }
                            }
                        }

                        private void closeAggregator() {
                            if (aggregator != null) {
                                aggregator.close();
                                aggregator = null;
                            }
                        }
                    });
                }
            });

            serverChannel = sb.bind().asStage().get();
            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();

            latch.await();
            Object received = clientReceived.get();
            if (received instanceof Buffer) {
                try (Buffer actual = (Buffer) received) {
                    assertEquals(expectedContent, actual);
                }
            } else {
                throw (Throwable) received;
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
        }
    }

    protected void compositeBufferPartialWriteDoesNotCorruptDataInitServerConfig(Channel channel,
                                                                                 int soSndBuf) {
    }

    private static Buffer newCompositeBuffer(BufferAllocator alloc) {
        CompositeBuffer compositeBuffer = alloc.compose(asList(
                alloc.allocate(4).writeInt(100).send(),
                alloc.allocate(8).writeLong(123).send(),
                alloc.allocate(8).writeLong(456).send()));
        assertEquals(EXPECTED_BYTES, compositeBuffer.readableBytes());
        return compositeBuffer;
    }

    private static byte[] newRandomBytes(int size, Random r) {
        byte[] bytes = new byte[size];
        r.nextBytes(bytes);
        return bytes;
    }
}
