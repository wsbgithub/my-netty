package com.pp.netty.channel;

import java.net.SocketAddress;

/**
 * @Author: PP-jessica
 * @Description:这个接口定义了channel出站时候的方法
 */
public interface ChannelOutboundInvoker {

    ChannelFuture bind(SocketAddress localAddress);

    ChannelFuture connect(SocketAddress remoteAddress);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    ChannelFuture disconnect();

    ChannelFuture close();

    ChannelFuture deregister();

    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture disconnect(ChannelPromise promise);

    ChannelFuture close(ChannelPromise promise);

    ChannelFuture deregister(ChannelPromise promise);

    ChannelOutboundInvoker read();

    ChannelFuture write(Object msg);

    ChannelFuture write(Object msg, ChannelPromise promise);

    ChannelOutboundInvoker flush();

    ChannelFuture writeAndFlush(Object msg, ChannelPromise promise);

    ChannelFuture writeAndFlush(Object msg);

    ChannelPromise newPromise();

    ChannelFuture newSucceededFuture();

    ChannelFuture newFailedFuture(Throwable cause);
}
