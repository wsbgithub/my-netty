package com.pp.netty.channel;



/**
 * @Author: PP-jessica
 * @Description:入站处理器的接口
 */
public interface ChannelInboundHandler extends ChannelHandler {


    void channelRegistered(ChannelHandlerContext ctx) throws Exception;


    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;


    void channelActive(ChannelHandlerContext ctx) throws Exception;


    void channelInactive(ChannelHandlerContext ctx) throws Exception;


    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;


    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;


    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;


    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;


    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
