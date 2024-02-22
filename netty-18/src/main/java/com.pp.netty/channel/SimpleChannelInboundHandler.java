package com.pp.netty.channel;


import com.pp.netty.util.ReferenceCountUtil;
import com.pp.netty.util.internal.TypeParameterMatcher;

/**
 * @Author: PP-jessica
 * @Description:一个内置的handler，这个handler可以处理引用计数，自动减1
 */
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {

    private final TypeParameterMatcher matcher;
    private final boolean autoRelease;

    //这里是假实现，该类还不能使用，因为这个构造器会在其子类创建对象的时候被调用
    //进而调用到下面的构造器
    protected SimpleChannelInboundHandler() {
        this(true);
    }

    //在这个构造器中会创建matcher匹配器，但是TypeParameterMatcher.find的方法在TypeParameterMatcher类中没有得到真正实现
    //后面我们会补全这一点
    //现在引入这个类，只是为了让大家看一下引用计数自动减1是怎么实现的
    protected SimpleChannelInboundHandler(boolean autoRelease) {
        matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
        this.autoRelease = autoRelease;
    }


    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType) {
        this(inboundMessageType, true);
    }


    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType, boolean autoRelease) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
        this.autoRelease = autoRelease;
    }


    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                channelRead0(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                //这个地方引用计数减1
                ReferenceCountUtil.release(msg);
            }
        }
    }


    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception;
}
