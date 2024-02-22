package com.pp.netty.channel.socket;

import com.pp.netty.util.internal.UnstableApi;

import java.io.IOException;

@UnstableApi
public final class ChannelOutputShutdownException extends IOException {
    private static final long serialVersionUID = 6712549938359321378L;

    public ChannelOutputShutdownException(String msg) {
        super(msg);
    }

    public ChannelOutputShutdownException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
