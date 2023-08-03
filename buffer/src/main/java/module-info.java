module io.netty5.buffer {
    exports io.netty5.buffer.internal;
    exports io.netty5.buffer;
    requires io.netty5.common;
    requires jdk.unsupported;
    requires org.jetbrains.annotations;
    requires org.slf4j;
}
