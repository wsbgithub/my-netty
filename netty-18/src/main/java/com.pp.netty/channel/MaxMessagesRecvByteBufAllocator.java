package com.pp.netty.channel;

public interface MaxMessagesRecvByteBufAllocator extends RecvByteBufAllocator {

    int maxMessagesPerRead();


    MaxMessagesRecvByteBufAllocator maxMessagesPerRead(int maxMessagesPerRead);
}