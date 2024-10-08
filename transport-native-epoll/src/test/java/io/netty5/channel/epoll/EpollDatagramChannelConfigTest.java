/*
 * Copyright 2019 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollDatagramChannelConfigTest {

    @Test
    public void testIpFreeBind() throws Exception {
        Epoll.ensureAvailability();

        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollDatagramChannel channel = new EpollDatagramChannel(group.next());
            channel.setOption(EpollChannelOption.IP_FREEBIND, true);
            assertTrue(channel.getOption(EpollChannelOption.IP_FREEBIND));
            channel.fd().close();
        } finally {
            group.shutdownGracefully();
        }
    }
}
