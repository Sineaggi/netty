/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.channel.Channel;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.unix.tests.DetectPeerCloseWithoutReadTest;

public class KQueueDetectPeerCloseWithoutReadTest extends DetectPeerCloseWithoutReadTest {
    @Override
    protected EventLoopGroup newGroup() {
        return new MultithreadEventLoopGroup(2, KQueueIoHandler.newFactory());
    }

    @Override
    protected Class<? extends ServerChannel> serverChannel() {
        return KQueueServerSocketChannel.class;
    }

    @Override
    protected Class<? extends Channel> clientChannel() {
        return KQueueSocketChannel.class;
    }
}
