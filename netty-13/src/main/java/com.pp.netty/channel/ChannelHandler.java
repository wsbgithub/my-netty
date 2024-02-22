package com.pp.netty.channel;

import java.lang.annotation.*;


/**
 * @Author: PP-jessica
 * @Description:ChannelHandler是ChannelPipeline的核心，对每一次接收到的数据的处理，靠的就是ChannelHandler
 */
public interface ChannelHandler {


    void handlerAdded(ChannelHandlerContext ctx) throws Exception;


    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;


    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;



    /**
     * @Author: PP-jessica
     * @Description:ChannelHandler是否可以公用的注解，这要考虑到并发问题。
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {

    }
}
