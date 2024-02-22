package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.ChannelHandler;
import com.pp.netty.channel.ChannelOption;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.nio.NioServerSocketChannel;
import com.pp.netty.util.AttributeKey;
import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ServerTest {

   public static AttributeKey<Integer> INDEX_KEY = AttributeKey.valueOf("常量");
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
        ChannelFuture channelFuture = bootstrap.group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                option(ChannelOption.SO_BACKLOG,2).
                handler(new TestHandlerOne()).
                attr(INDEX_KEY,10).
                childAttr(INDEX_KEY,10).
                bind(8080).
                addListener(future -> System.out.println("我绑定成功了")).sync();
    }
}
