/*
 * Copyright 2019 The Netty Project
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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.netty5.handler.codec.http.HttpUtil.isKeepAlive;

/**
 * Handles the HTTP handshake (the HTTP Upgrade request) for {@link WebSocketServerProtocolHandler}.
 */
class WebSocketServerProtocolHandshakeHandler implements ChannelHandler {

    private final WebSocketServerProtocolConfig serverConfig;
    private Promise<Void> handshakePromise;
    private boolean isWebSocketPath;

    WebSocketServerProtocolHandshakeHandler(WebSocketServerProtocolConfig serverConfig) {
        this.serverConfig = Objects.requireNonNull(serverConfig, "serverConfig");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakePromise = ctx.newPromise();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        final HttpObject httpObject = (HttpObject) msg;

        if (httpObject instanceof HttpRequest) {
            final HttpRequest req = (HttpRequest) httpObject;
            isWebSocketPath = isWebSocketPath(req);
            if (!isWebSocketPath) {
                ctx.fireChannelRead(msg);
                return;
            }

            try {
                final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                        getWebSocketLocation(ctx.pipeline(), req, serverConfig.websocketPath()),
                        serverConfig.subprotocols(), serverConfig.decoderConfig());
                final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
                Promise<Void> localHandshakePromise = handshakePromise;
                if (handshaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    // Ensure we set the handshaker and replace this handler before we
                    // trigger the actual handshake. Otherwise we may receive websocket bytes in this handler
                    // before we had a chance to replace it.
                    //
                    // See https://github.com/netty/netty/issues/9471.
                    WebSocketServerProtocolHandler.setHandshaker(ctx.channel(), handshaker);

                    Future<Void> handshakeFuture = handshaker.handshake(ctx.channel(), req);
                    handshakeFuture.addListener(future -> {
                        if (future.isFailed()) {
                            localHandshakePromise.tryFailure(future.cause());
                            ctx.fireChannelExceptionCaught(future.cause());
                        } else {
                            localHandshakePromise.trySuccess(null);
                            ctx.fireChannelInboundEvent(
                                    new WebSocketServerHandshakeCompletionEvent(handshaker.version(),
                                            req.uri(), req.headers(), handshaker.selectedSubprotocol()));
                        }
                        ctx.pipeline().remove(this);
                    });
                    applyHandshakeTimeout(ctx);
                }
            } finally {
                if (req instanceof AutoCloseable) {
                    ((AutoCloseable) req).close();
                }
            }
        } else if (!isWebSocketPath) {
            ctx.fireChannelRead(msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private boolean isWebSocketPath(HttpRequest req) {
        String websocketPath = serverConfig.websocketPath();
        String uri = req.uri();

        return serverConfig.checkStartsWith()
                ? uri.startsWith(websocketPath) && ("/".equals(websocketPath) || checkNextUri(uri, websocketPath))
                : uri.equals(websocketPath);
    }

    private static boolean checkNextUri(String uri, String websocketPath) {
        int len = websocketPath.length();
        if (uri.length() > len) {
            char nextUri = uri.charAt(len);
            return nextUri == '/' || nextUri == '?';
        }
        return true;
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        Future<Void> f = ctx.writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ctx, ChannelFutureListeners.CLOSE);
        }
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        CharSequence host = req.headers().get(HttpHeaderNames.HOST);
        return protocol + "://" + host + path;
    }

    private void applyHandshakeTimeout(ChannelHandlerContext ctx) {
        Promise<Void> localHandshakePromise = handshakePromise;
        final long handshakeTimeoutMillis = serverConfig.handshakeTimeoutMillis();
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final Future<?> timeoutFuture = ctx.executor().schedule(() -> {
            if (localHandshakePromise.isDone()) {
                return;
            }
            WebSocketHandshakeException exception = new WebSocketHandshakeTimeoutException(
                    "handshake timed out after " + handshakeTimeoutMillis + "ms");
            if (localHandshakePromise.tryFailure(exception)) {
                ctx.flush()
                   .fireChannelInboundEvent(new WebSocketServerHandshakeCompletionEvent(exception))
                   .close();
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.asFuture().addListener(f -> timeoutFuture.cancel());
    }
}
