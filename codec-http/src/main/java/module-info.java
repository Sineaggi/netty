module io.netty5.codec.http {
    exports io.netty5.handler.codec.http.headers;
    exports io.netty5.handler.codec.http;
    exports io.netty5.handler.codec.http.websocketx;
    exports io.netty5.handler.codec.http.websocketx.extensions.compression;
    exports io.netty5.handler.codec.http.cors;
    requires io.netty5.buffer;
    requires io.netty5.codec;
    requires io.netty5.common;
    requires io.netty5.handler;
    requires io.netty5.transport;
    requires org.jetbrains.annotations;
    requires org.slf4j;
}
