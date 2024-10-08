/*
 * Copyright 2012 The Netty Project
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
package io.netty5.example.discard;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.example.util.ServerUtil;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.SslContext;

/**
 * Discards any incoming data.
 */
public final class DiscardServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx = ServerUtil.buildSslContext();

        EventLoopGroup bossGroup = new MultithreadEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.bufferAllocator()));
                     }
                     p.addLast(new DiscardServerHandler());
                 }
             });

            // Bind and start to accept incoming connections.
            Channel channel = b.bind(PORT).asStage().get();

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            channel.closeFuture().asStage().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
