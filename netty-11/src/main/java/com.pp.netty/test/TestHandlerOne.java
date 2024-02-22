package com.pp.netty.test;

import com.pp.netty.channel.ChannelHandlerContext;
import com.pp.netty.channel.ChannelInboundHandlerAdapter;
import com.pp.netty.channel.EventLoop;
import com.pp.netty.handler.timeout.IdleState;
import com.pp.netty.handler.timeout.IdleStateEvent;
import com.pp.netty.util.Attribute;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;


public class TestHandlerOne extends ChannelInboundHandlerAdapter {

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuffer byteBuffer = (ByteBuffer)msg;
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        System.out.println("channelRead中输出的+++++++客户端收到消息:{}"+new String(bytes));
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        EventLoop  executors = ctx.channel().eventLoop();
        executors.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("1秒1次");
            }
        },0,1, TimeUnit.SECONDS);
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            IdleStateEvent event = (IdleStateEvent)evt;
            if (event.state()== IdleState.READER_IDLE){
                System.out.println("触发了读空闲事件！");
            }
            if (event.state()== IdleState.WRITER_IDLE){
                System.out.println("触发了写空闲事件！");
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
