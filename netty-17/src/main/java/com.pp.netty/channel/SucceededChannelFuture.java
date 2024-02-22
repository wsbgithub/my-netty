package com.pp.netty.channel;

import com.pp.netty.util.concurrent.EventExecutor;

final class SucceededChannelFuture extends CompleteChannelFuture {


    SucceededChannelFuture(Channel channel, EventExecutor executor) {
        super(channel, executor);
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}