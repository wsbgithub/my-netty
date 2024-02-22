package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.*;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
        ChannelFuture channelFuture = serverBootstrap.group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                childHandler(new TestHandlerOne()).
                bind(8080).
                addListener(future -> System.out.println("我绑定端口号成功了")).sync();
        //优雅释放资源的方法
        //workerGroup.shutdownGracefully();
    }
}
