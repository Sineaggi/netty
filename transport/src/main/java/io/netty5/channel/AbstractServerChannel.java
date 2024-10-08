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
package io.netty5.channel;

import io.netty5.buffer.Buffer;

import java.net.SocketAddress;

/**
 * A skeletal {@link ServerChannel} implementation.
 */
public abstract class AbstractServerChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractChannel<P, L, R> implements ServerChannel {

    private final EventLoopGroup childEventLoopGroup;

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                    Class<? extends IoHandle> ioHandleType) {
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(),
                new ServerChannelWriteHandleFactory(), ioHandleType);
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", ioHandleType);
    }

    @Override
    public final EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @Override
    protected final R remoteAddress0() {
        return null;
    }

    @Override
    protected final void doDisconnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final void doShutdown(ChannelShutdownDirection direction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isShutdown(ChannelShutdownDirection direction) {
        return !isActive();
    }

    @Override
    protected final void doWriteNow(WriteSink writeSink) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final boolean doFinishConnect(R requestedRemoteAddress) {
        throw new UnsupportedOperationException();
    }
}
