package com.pp.netty.channel;

import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.util.Attribute;
import com.pp.netty.util.AttributeKey;
import com.pp.netty.util.AttributeMap;
import com.pp.netty.util.concurrent.EventExecutor;


/**
 * @Author: PP-jessica
 * @Description:该类是ChannelPipeline中包装handler的类，里面封装着ChannelHandler，和每一个handler的上下文信息
 * 而正是一个个ChannelHandlerContext对象，构成了ChannelPipeline中责任链的链表，链表的每一个节点都是ChannelHandlerContext
 * 对象，每一个ChannelHandlerContext对象里都有一个handler。这个接口也继承了出站入站方法，想一想，这个接口的继承接口，为什么要继承入站出站
 * 的接口呢？
 * 同时也应该注意到该接口继承了AttributeMap接口，这说明ChannelHandlerContext的实现类本身也是一个map，那么用户存储在该实现类中的
 * 参数，在某些类中应该也是可以获得的。
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker{

    Channel channel();

    EventExecutor executor();

    String name();

    ChannelHandler handler();

    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    ByteBufAllocator alloc();

    ChannelPipeline pipeline();

    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);


    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
