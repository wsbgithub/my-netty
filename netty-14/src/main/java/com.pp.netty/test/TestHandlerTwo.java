package com.pp.netty.test;

import com.pp.netty.channel.ChannelHandlerContext;
import com.pp.netty.channel.ChannelInboundHandlerAdapter;
import com.pp.netty.util.Attribute;


public class TestHandlerTwo extends ChannelInboundHandlerAdapter {

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("TestHandlerTwo 第一个回调 handlerAdded");
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("TestHandlerTwo 第二个回调 channelRegistered");
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("TestHandlerTwo 第三个回调 channelActive");
        super.channelActive(ctx);
    }
}
