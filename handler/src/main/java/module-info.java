module io.netty5.handler {
    exports io.netty5.handler.logging;
    exports io.netty5.handler.ssl;
    exports io.netty5.handler.stream;
    exports io.netty5.handler.ssl.util;
    exports io.netty5.handler.timeout;
    exports io.netty5.handler.ssl.ocsp;
    exports io.netty5.handler.ipfilter;
    requires io.netty.tcnative.classes.openssl;
    requires io.netty5.buffer;
    requires io.netty5.codec;
    requires io.netty5.common;
    requires io.netty5.resolver;
    requires io.netty5.transport.unix.common;
    requires io.netty5.transport;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.conscrypt;
    requires org.jetbrains.annotations;
    requires org.slf4j;
}
