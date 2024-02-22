package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.NioServerSocketChannel;
import com.pp.netty.util.concurrent.DefaultPromise;
import com.pp.netty.util.concurrent.Future;
import com.pp.netty.util.concurrent.GenericFutureListener;
import com.pp.netty.util.concurrent.Promise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;

public class TestNioo {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
        ChannelFuture channelFuture = serverBootstrap.group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                bind(8080).addListener(future -> System.out.println("我绑定成功了")).sync();
    }
}
