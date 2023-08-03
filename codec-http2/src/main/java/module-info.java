module io.netty5.codec.http2 {
    exports io.netty5.handler.codec.http2;
    exports io.netty5.handler.codec.http2.headers;
    requires io.netty5.buffer;
    requires io.netty5.codec.http;
    requires io.netty5.codec;
    requires io.netty5.common;
    requires io.netty5.handler;
    requires io.netty5.transport;
    requires org.jetbrains.annotations;
    requires org.slf4j;
}
