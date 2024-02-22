package com.pp.netty.channel.socket;

import com.pp.netty.channel.ServerChannel;

import java.net.InetSocketAddress;

public interface ServerSocketChannel extends ServerChannel {
    @Override
    ServerSocketChannelConfig config();
    @Override
    InetSocketAddress localAddress();
    @Override
    InetSocketAddress remoteAddress();
}