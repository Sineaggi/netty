module io.netty5.codec {
    exports io.netty5.handler.codec.base64;
    exports io.netty5.handler.codec.compression;
    exports io.netty5.handler.codec;
    exports io.netty5.handler.codec.string;
    requires com.aayushatharva.brotli4j;
    requires com.github.luben.zstd_jni;
    requires compress.lzf;
    requires io.netty5.buffer;
    requires io.netty5.common;
    requires io.netty5.transport;
    requires lzma.java;
    requires org.jetbrains.annotations;
    requires org.lz4.java;
    requires org.slf4j;
}
