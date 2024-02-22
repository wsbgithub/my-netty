package com.pp.netty.channel;

import java.util.Map;

public abstract class ChannelHandlerAdapter implements ChannelHandler {


    boolean added;

    protected void ensureNotSharable() {
        if (isSharable()) {
            throw new IllegalStateException("ChannelHandler " + getClass().getName() + " is not allowed to be shared");
        }
    }


    public boolean isSharable() {
//        Class<?> clazz = getClass();
//        Map<Class<?>, Boolean> cache = InternalThreadLocalMap.get().handlerSharableCache();
//        Boolean sharable = cache.get(clazz);
//        if (sharable == null) {
//            sharable = clazz.isAnnotationPresent(Sharable.class);
//            cache.put(clazz, sharable);
//        }
//        return sharable;
        return true;
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }


    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }


    @ChannelHandlerMask.Skip
    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
