package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.*;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.nio.NioServerSocketChannel;
import com.pp.netty.util.AttributeKey;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class ServerTest {

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        ChannelFuture channelFuture = bootstrap.group(bossGroup, workGroup).
                channel(NioServerSocketChannel.class).
                handler(new TestHandlerTwo()).
                option(ChannelOption.SO_BACKLOG,128).
                childAttr(AttributeKey.valueOf("常量"),10).
                childHandler(new TestHandlerOne()).
                bind(8080).
                addListener(future -> System.out.println("我绑定成功了")).sync();
    }
}
