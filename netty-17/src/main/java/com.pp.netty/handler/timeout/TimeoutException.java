package com.pp.netty.handler.timeout;

import com.pp.netty.channel.ChannelException;


public class TimeoutException extends ChannelException {

    private static final long serialVersionUID = 4673641882869672533L;

    TimeoutException() {
    }

    TimeoutException(boolean shared) {
        super(null, null, shared);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}