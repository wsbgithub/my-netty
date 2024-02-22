package com.pp.netty.channel;

import java.net.SocketAddress;

import static com.pp.netty.util.internal.ObjectUtil.checkPositive;


public final class ChannelMetadata {

    private final boolean hasDisconnect;
    //这个值为16，创建ChannelMetadata对象的时候会发现该值被设置成16了
    private final int defaultMaxMessagesPerRead;

    public ChannelMetadata(boolean hasDisconnect) {
        this(hasDisconnect, 1);
    }


    public ChannelMetadata(boolean hasDisconnect, int defaultMaxMessagesPerRead) {
        checkPositive(defaultMaxMessagesPerRead, "defaultMaxMessagesPerRead");
        this.hasDisconnect = hasDisconnect;
        this.defaultMaxMessagesPerRead = defaultMaxMessagesPerRead;
    }


    public boolean hasDisconnect() {
        return hasDisconnect;
    }


    public int defaultMaxMessagesPerRead() {
        return defaultMaxMessagesPerRead;
    }
}
