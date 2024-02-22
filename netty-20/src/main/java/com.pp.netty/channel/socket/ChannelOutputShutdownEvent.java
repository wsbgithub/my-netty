package com.pp.netty.channel.socket;

import com.pp.netty.util.internal.UnstableApi;

@UnstableApi
public final class ChannelOutputShutdownEvent {
    public static final ChannelOutputShutdownEvent INSTANCE = new ChannelOutputShutdownEvent();

    private ChannelOutputShutdownEvent() {
    }
}
