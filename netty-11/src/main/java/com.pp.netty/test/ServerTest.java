package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.*;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.nio.NioServerSocketChannel;
import com.pp.netty.channel.socket.nio.NioSocketChannel;
import com.pp.netty.handler.timeout.IdleStateHandler;

import java.io.IOException;
import java.util.LinkedList;

public class ServerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
        ChannelFuture channelFuture = serverBootstrap.group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline  p = ch.pipeline();
                        p.addLast(new IdleStateHandler(1,1,0));
                        p.addLast(new TestHandlerOne());
                    }
                }).
                bind(8080).
                addListener(future -> System.out.println("我绑定端口号成功了")).sync();

    }
}
