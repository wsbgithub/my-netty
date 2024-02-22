package com.pp.netty.channel;

import com.pp.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @Author: PP-jessica
 * @Description:channel的顶级接口，引入了ChannelPipeline
 */
public interface Channel extends AttributeMap,  ChannelOutboundInvoker{

    ChannelId id();


    EventLoop eventLoop();


    Channel parent();

    /**
     * @Author: PP-jessica
     * @Description:这一节课引入channelConfig
     */
    ChannelConfig config();


    boolean isOpen();


    boolean isRegistered();


    boolean isActive();


    SocketAddress localAddress();


    SocketAddress remoteAddress();


    ChannelFuture closeFuture();

    /**
     * @Author: PP-jessica
     * @Description:终于引入了Unsafe类
     */
    Unsafe unsafe();

    /**
     * @Author: PP-jessica
     * @Description:终于引入了ChannelPipeline
     */
    ChannelPipeline pipeline();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * @Author: PP-jessica
     * @Description:看到这个接口中的方法，是不是发现很多都和ChannelOutboundInvoker这个类中的重复？
     * 稍微想一想就会明白，channel调用方法，但真正执行还是由unsafe的实现类来执行，虽然最后有可能还是调用到channel中
     */
    interface Unsafe {

        SocketAddress localAddress();

        SocketAddress remoteAddress();

        void register(EventLoop eventLoop, ChannelPromise promise);

        void bind(SocketAddress localAddress, ChannelPromise promise);

        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        void disconnect(ChannelPromise promise);

        void close(ChannelPromise promise);

        void closeForcibly();

        void deregister(ChannelPromise promise);

        void beginRead();

        void write(Object msg, ChannelPromise promise);

        void flush();


    }
}
