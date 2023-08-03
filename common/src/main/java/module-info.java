module io.netty5.common {
    exports io.netty5.util.collection;
    exports io.netty5.util.concurrent;
    exports io.netty5.util.internal;
    exports io.netty5.util;
    requires jctools.core;
    requires jdk.unsupported;
    requires org.jetbrains.annotations;
    requires org.slf4j;
    requires reactor.blockhound;
}