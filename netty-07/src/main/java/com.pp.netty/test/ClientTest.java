package com.pp.netty.test;

import com.pp.netty.bootstrap.Bootstrap;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ClientTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture channelFuture = bootstrap.group(workerGroup).
                                                channel(NioSocketChannel.class).
                                                connect("127.0.0.1",8080).sync();
        Channel channel = channelFuture.channel();
        channel.writeAndFlush(ByteBuffer.wrap("我是真正的netty！".getBytes()));
        System.out.println("发送成功了！");
    }

}
