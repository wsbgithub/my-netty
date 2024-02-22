package com.pp.netty.channel;

import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.util.concurrent.EventExecutorGroup;

import java.nio.channels.ServerSocketChannel;


/**
 * @Author: PP-jessica
 * @Description:事件循环组接口
 */
public interface EventLoopGroup extends EventExecutorGroup {

    @Override
    EventLoop next();


}
