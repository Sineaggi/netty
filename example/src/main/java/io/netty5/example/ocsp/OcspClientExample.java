/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.example.ocsp;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty5.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandler;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.ocsp.OcspClientHandler;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Promise;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import javax.net.ssl.SSLSession;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * This is a very simple example for an HTTPS client that uses OCSP stapling.
 * The client connects to an HTTPS server that has OCSP stapling enabled and
 * then uses BC to parse and validate it.
 */
public class OcspClientExample {
    public static void main(String[] args) throws Exception {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException("OpenSSL is not available!");
        }

        if (!OpenSsl.isOcspSupported()) {
            throw new IllegalStateException("OCSP is not supported!");
        }

        // Using Wikipedia as an example. I'd rather use Netty's own website
        // but the server (Cloudflare) doesn't support OCSP stapling. A few
        // other examples could be Microsoft or Squarespace. Use OpenSSL's
        // CLI client to assess if a server supports OCSP stapling. E.g.:
        //
        // openssl s_client -tlsextdebug -status -connect www.squarespace.com:443
        //
        String host = "www.wikipedia.org";

        ReferenceCountedOpenSslContext context
            = (ReferenceCountedOpenSslContext) SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .enableOcsp(true)
                .build();

        try {
            EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
            try {
                Promise<FullHttpResponse> promise = group.next().newPromise();

                Bootstrap bootstrap = new Bootstrap()
                        .channel(NioSocketChannel.class)
                        .group(group)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
                        .handler(newClientHandler(context, host, promise));

                Channel channel = bootstrap.connect(host, 443).asStage().get();

                try {
                    FullHttpResponse response = promise.asFuture().asStage().get();
                    Resource.dispose(response);
                } finally {
                    channel.close();
                }
            } finally {
                group.shutdownGracefully();
            }
        } finally {
            context.release();
        }
    }

    private static ChannelInitializer<Channel> newClientHandler(final ReferenceCountedOpenSslContext context,
            final String host, final Promise<FullHttpResponse> promise) {

        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                SslHandler sslHandler = context.newHandler(ch.bufferAllocator());
                ReferenceCountedOpenSslEngine engine =
                        (ReferenceCountedOpenSslEngine) sslHandler.engine();

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(sslHandler);
                pipeline.addLast(new ExampleOcspClientHandler(engine));

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                pipeline.addLast(new HttpClientHandler(host, promise));
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                if (!promise.isDone()) {
                    promise.tryFailure(new IllegalStateException("Connection closed and Promise was not done."));
                }
                ctx.fireChannelInactive();
            }

            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (!promise.tryFailure(cause)) {
                    ctx.fireChannelExceptionCaught(cause);
                }
            }
        };
    }

    private static class HttpClientHandler implements ChannelHandler {

        private final String host;

        private final Promise<FullHttpResponse> promise;

        HttpClientHandler(String host, Promise<FullHttpResponse> promise) {
            this.host = host;
            this.promise = promise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            FullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, "/", ctx.bufferAllocator().allocate(0));
            request.headers().set(HttpHeaderNames.HOST, host);
            request.headers().set(HttpHeaderNames.USER_AGENT, "netty-ocsp-example/1.0");

            ctx.writeAndFlush(request).addListener(ctx.channel(), ChannelFutureListeners.FIRE_EXCEPTION_ON_FAILURE);

            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!promise.isDone()) {
                promise.tryFailure(new IllegalStateException("Connection closed and Promise was not done."));
            }
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpResponse) {
                if (!promise.trySuccess((FullHttpResponse) msg)) {
                    Resource.dispose(msg);
                }
                return;
            }

            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (!promise.tryFailure(cause)) {
                ctx.fireChannelExceptionCaught(cause);
            }
        }
    }

    private static class ExampleOcspClientHandler extends OcspClientHandler {

        ExampleOcspClientHandler(ReferenceCountedOpenSslEngine engine) {
            super(engine);
        }

        @Override
        protected boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine) throws Exception {
            byte[] staple = engine.getOcspResponse();
            if (staple == null) {
                throw new IllegalStateException("Server didn't provide an OCSP staple!");
            }

            OCSPResp response = new OCSPResp(staple);
            if (response.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
                return false;
            }

            SSLSession session = engine.getSession();
            Certificate[] chain = session.getPeerCertificates();
            BigInteger certSerial = ((X509Certificate) chain[0]).getSerialNumber();

            BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
            SingleResp first = basicResponse.getResponses()[0];

            // ATTENTION: CertificateStatus.GOOD is actually a null value! Do not use
            // equals() or you'll NPE!
            CertificateStatus status = first.getCertStatus();
            BigInteger ocspSerial = first.getCertID().getSerialNumber();
            String message = new StringBuilder()
                .append("OCSP status of ").append(ctx.channel().remoteAddress())
                .append("\n  Status: ").append(status == CertificateStatus.GOOD ? "Good" : status)
                .append("\n  This Update: ").append(first.getThisUpdate())
                .append("\n  Next Update: ").append(first.getNextUpdate())
                .append("\n  Cert Serial: ").append(certSerial)
                .append("\n  OCSP Serial: ").append(ocspSerial)
                .toString();
            System.out.println(message);

            return status == CertificateStatus.GOOD && certSerial.equals(ocspSerial);
        }
    }
}
