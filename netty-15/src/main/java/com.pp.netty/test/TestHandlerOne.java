package com.pp.netty.test;

import com.pp.netty.channel.ChannelHandlerContext;
import com.pp.netty.channel.ChannelInboundHandlerAdapter;
import com.pp.netty.channel.EventLoop;
import com.pp.netty.util.Attribute;



import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;


public class TestHandlerOne extends ChannelInboundHandlerAdapter {

    /**
     * @Author: PP-jessica
     * @Description:验证一下我们这节课着重讲解的回调方法，根据我们讲解的内容，应该是handlerAdded方法最先被回调，因为当服务端channel
     * 注册到单线程执行器成功的那一刻， pipeline.invokeHandlerAddedIfNeeded()就会被执行，接着会执行pipeline.fireChannelRegistered();
     * 最后，在NioServerSocketChannel绑定端口号成功之后，执行pipeline.fireChannelActive();，表明通channel被激活了
     * 这里我们就按顺序验证一下。因为没有测试客户端发送数据，所以我们暂时不测试channelRead方法。
     * 当然，随着我们课程的进展，handler中的回调方法都会被我们讲解到。
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuffer buffer = (ByteBuffer)msg;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        System.out.println("发送的是：channelRead中输出的+++++++客户端收到消息:{}"+new String(bytes));
        //这里加上这一行代码，就会达到一个死循环的效果，因为服务端接收到消息发给客户端，客户端仍然是用这个处理器来处理的，又会发送服务端
        //消息，然后服务端又发给客户端，如此循环下去。如果你觉得你的cpu该锻炼一下了，就执行这行代码吧。
        //ctx.channel().writeAndFlush(ByteBuffer.wrap("我是真正的netty！".getBytes()));
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
//        EventLoop  executors = ctx.channel().eventLoop();
//        executors.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("1秒1次");
//            }
//        },0,1, TimeUnit.SECONDS);
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }
}
