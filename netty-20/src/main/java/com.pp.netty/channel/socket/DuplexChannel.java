package com.pp.netty.channel.socket;

import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.ChannelPromise;

import java.net.Socket;

public interface DuplexChannel extends Channel {

    boolean isInputShutdown();


    ChannelFuture shutdownInput();


    ChannelFuture shutdownInput(ChannelPromise promise);


    boolean isOutputShutdown();


    ChannelFuture shutdownOutput();


    ChannelFuture shutdownOutput(ChannelPromise promise);

    boolean isShutdown();


    ChannelFuture shutdown();


    ChannelFuture shutdown(ChannelPromise promise);
}