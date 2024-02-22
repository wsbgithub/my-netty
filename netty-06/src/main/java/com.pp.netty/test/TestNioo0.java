package com.pp.netty.test;

import com.pp.netty.bootstrap.Bootstrap;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.NioSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TestNioo0 {
    public static void main(String[] args) throws IOException, InterruptedException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).
                channel(NioSocketChannel.class);
        ChannelFuture channelFuture = bootstrap.connect("127.0.0.1",8080).sync();
        Thread.sleep(3000);
        Channel channel = channelFuture.channel();
        channel.writeAndFlush(ByteBuffer.wrap("我是真正的netty！".getBytes()));
        System.out.println("发送数据成功了");
    }

}
