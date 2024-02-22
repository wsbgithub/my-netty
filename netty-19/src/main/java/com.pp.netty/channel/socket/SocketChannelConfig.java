package com.pp.netty.channel.socket;

import com.pp.netty.channel.ChannelConfig;

import java.net.Socket;
import java.net.StandardSocketOptions;

public interface SocketChannelConfig extends ChannelConfig {


    boolean isTcpNoDelay();


    SocketChannelConfig setTcpNoDelay(boolean tcpNoDelay);


    int getSoLinger();


    SocketChannelConfig setSoLinger(int soLinger);


    int getSendBufferSize();


    SocketChannelConfig setSendBufferSize(int sendBufferSize);


    int getReceiveBufferSize();


    SocketChannelConfig setReceiveBufferSize(int receiveBufferSize);


    boolean isKeepAlive();


    SocketChannelConfig setKeepAlive(boolean keepAlive);


    int getTrafficClass();


    SocketChannelConfig setTrafficClass(int trafficClass);

    boolean isReuseAddress();

    SocketChannelConfig setReuseAddress(boolean reuseAddress);

    SocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth);


    boolean isAllowHalfClosure();

    SocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    SocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    SocketChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    SocketChannelConfig setAutoRead(boolean autoRead);

    @Override
    SocketChannelConfig setAutoClose(boolean autoClose);

}
