package com.pp.netty.test;

import com.pp.netty.bootstrap.Bootstrap;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.SimpleChannelInboundHandler;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.SocketChannel;
import com.pp.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ClientTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture channelFuture = bootstrap.group(workerGroup).
                channel(NioSocketChannel.class).
                handler(new TestHandlerOne()).
                connect("127.0.0.1",8080).sync();
        Channel channel = channelFuture.channel();
        channel.writeAndFlush(ByteBuffer.wrap("我是真正的netty！".getBytes()));
        //这是正常关闭客户端连接的方法
        channel.close();
        //下面是使用半连接状态来关闭channel
        //这种情况应该将channel转换成SocketChannel，注意，这里的这个SocketChannel是Netty自己定义的
        SocketChannel socketChannel = (SocketChannel) channel;
        //使用半连接关闭，当调用这个方法的时候，客户端channel就不能再向服务端发送消息了，就是把写缓冲区关闭的意思
        //在方法的内部，会进一步调用到Nio原生的方法，将channel关闭掉，注意，这里最后调用的也是nio中的半关闭的方法
        //可以点进去看一看
        //服务端接收到半关闭的消息后，会将其管理的客户端channel关闭
        //为什么要这么做呢？大家可以想一想，channel依靠了tcp协议，而tcp协议维护的只是一个状态
        //只要状态是正确的，channel就既可以发送消息又可以接收消息，简单来说，就是全双工的
        //现在客户端channel要关闭了，决定不再向服务端发送消息，但是如果服务端还有要向客户端发送的消息呢？
        //能接受消息而不能发送消息，调用这个方法就可以达到效果了
        socketChannel.shutdownOutput();
    }
}
