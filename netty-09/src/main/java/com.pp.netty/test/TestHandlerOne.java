package com.pp.netty.test;

import com.pp.netty.channel.ChannelHandlerContext;
import com.pp.netty.channel.ChannelInboundHandlerAdapter;
import com.pp.netty.util.Attribute;

import static com.pp.netty.test.ServerTest.INDEX_KEY;

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
        //注意，该方法不像其他方法那样需要向链表后面的节点传播，它本身就在另一个链表中被执行，如果要执行下一个节点中的handlerAdded方法
        //链表本身就会去寻找下一个，然后执行。
        //PendingHandlerCallback task = pendingHandlerCallbackHead;
        //         挨个执行任务列表中的任务
        //        while (task != null) {
        //            task.execute();
        //            task = task.next;
        //        }就是这段代码。
        //pipeline.invokeHandlerAddedIfNeeded()在这段代码中debug就可以看见调用逻辑
        System.out.println("第一个回调 handlerAdded");
        super.handlerAdded(ctx);
    }


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("第二个回调 channelRegistered");
        //下面这个代码回调用到父类中，然后调用下一个节点的channelRegistered方法
        super.channelRegistered(ctx);
    }

    /**
     * @Author: PP-jessica
     * @Description:就在这个方法中测试AttributeMap的用法吧。
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("第三个回调 channelActive");
        Attribute<Integer> attribute = ctx.attr(INDEX_KEY);
        Integer integer = attribute.get();
        System.out.println("attribute中存储着常量" + integer + "这说明channel中存储的数据在每个handler中都可以获得！");
        super.channelActive(ctx);
    }

}
