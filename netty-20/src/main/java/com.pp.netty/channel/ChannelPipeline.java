package com.pp.netty.channel;

import com.pp.netty.util.concurrent.EventExecutorGroup;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


/**
 * @Author: PP-jessica
 * @Description:可以看到ChannelPipeline接口继承了入站方法的接口和出站方法的接口，而我们知道ChannelPipeline只是个通道，或者说管道，
 * 在该通道中真正处理数据的是ChannelHandler，由此可以想到，该接口这样定义，肯定是在ChannelPipeline调用入站或者出站方法，而由每一个handler
 * 去处理，可想而知，ChannelHandler肯定也会通过一些方式，继承接口或者继承某些类，从而可以调用出站或入站方法
 * 所以在这个接口中，我们的关注重点肯定要放在前面这些addxx方法上，这些就是把ChannelHandler添加进ChannelPipeline的重要方法
 * 当然，我们不会每个方法都去实现，讲解完核心的方法后去看源码，会很容易理解其他的方法
 */
public interface ChannelPipeline  extends
        ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Map.Entry<String, ChannelHandler>>{


    ChannelPipeline addFirst(String name, ChannelHandler handler);

    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    ChannelPipeline addLast(String name, ChannelHandler handler);

    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    ChannelPipeline addFirst(ChannelHandler... handlers);

    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    ChannelPipeline addLast(ChannelHandler... handlers);

    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    ChannelPipeline remove(ChannelHandler handler);

    ChannelHandler remove(String name);

    <T extends ChannelHandler> T remove(Class<T> handlerType);

    ChannelHandler removeFirst();

    ChannelHandler removeLast();

    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);
    ChannelHandler first();

    ChannelHandlerContext firstContext();

    ChannelHandler last();

    ChannelHandlerContext lastContext();

    ChannelHandler get(String name);

    <T extends ChannelHandler> T get(Class<T> handlerType);

    ChannelHandlerContext context(ChannelHandler handler);

    ChannelHandlerContext context(String name);

    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    Channel channel();

    List<String> names();

    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();

}
