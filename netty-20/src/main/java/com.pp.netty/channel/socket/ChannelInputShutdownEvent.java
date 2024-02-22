package com.pp.netty.channel.socket;

public final class ChannelInputShutdownEvent {

    @SuppressWarnings("InstantiationOfUtilityClass")
    public static final ChannelInputShutdownEvent INSTANCE = new ChannelInputShutdownEvent();

    private ChannelInputShutdownEvent() { }
}
