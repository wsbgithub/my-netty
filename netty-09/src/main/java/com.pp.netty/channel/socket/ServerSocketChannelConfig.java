package com.pp.netty.channel.socket;

import com.pp.netty.channel.ChannelConfig;

import java.net.ServerSocket;
import java.net.StandardSocketOptions;


public interface ServerSocketChannelConfig extends ChannelConfig {

    int getBacklog();

    ServerSocketChannelConfig setBacklog(int backlog);

    boolean isReuseAddress();

    ServerSocketChannelConfig setReuseAddress(boolean reuseAddress);

    int getReceiveBufferSize();

    ServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);

    ServerSocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);

    @Override
    ServerSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    ServerSocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    ServerSocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    ServerSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    ServerSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);


}
