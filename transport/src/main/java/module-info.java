module io.netty5.transport {
    exports io.netty5.bootstrap;
    exports io.netty5.channel.embedded;
    exports io.netty5.channel.internal;
    exports io.netty5.channel.socket;
    exports io.netty5.channel;
    exports io.netty5.channel.local;
    exports io.netty5.channel.nio;
    exports io.netty5.channel.socket.nio;
    exports io.netty5.channel.group;
    requires io.netty5.buffer;
    requires io.netty5.common;
    requires io.netty5.resolver;
    requires org.slf4j;
    requires org.jetbrains.annotations;
}
