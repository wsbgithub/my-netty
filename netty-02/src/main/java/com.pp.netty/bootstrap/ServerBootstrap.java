package com.pp.netty.bootstrap;

import com.pp.netty.channel.EventLoop;
import com.pp.netty.channel.EventLoopGroup;
import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

public class ServerBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private NioEventLoop nioEventLoop;

    private ServerSocketChannel serverSocketChannel;

    public ServerBootstrap() {

    }

    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        this.bossGroup = parentGroup;
        this.workerGroup = childGroup;
        return this;
    }

    public ServerBootstrap serverSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
        return this;
    }

    public void bind(String host,int inetPort) {
        bind(new InetSocketAddress(host,inetPort));
    }

    public void bind(SocketAddress localAddress) {
        doBind(localAddress);
    }

    private void doBind(SocketAddress localAddress) {
        //得到boss事件循环组中的事件执行器，也就是单线程执行器,这个里面其实就包含一个单线程执行器，在workergroup中才包含多个单线程执行器
        //这里就暂时先写死了
        nioEventLoop =(NioEventLoop)bossGroup.next().next();
        nioEventLoop.setServerSocketChannel(serverSocketChannel);
        nioEventLoop.setWorkerGroup(workerGroup);
        //直接使用nioeventloop把服务端的channel注册到单线程执行器上
        nioEventLoop.register(serverSocketChannel,nioEventLoop);
        doBind0(localAddress);
    }

    /**
     * @Author: PP-jessica
     * @Description:这里把绑定端口号封装成一个runnable，提交到单线程执行器的任务队列，绑定端口号仍然由单线程执行器完成
     * 这时候执行器的线程已经启动了
     */
    private void doBind0(SocketAddress localAddress) {
        nioEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocketChannel.bind(localAddress);
                    logger.info("服务端channel绑定了服务器端口了");
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        });
    }
}
