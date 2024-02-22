package com.pp.netty.channel;

import com.pp.netty.buffer.ByteBufAllocator;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * @Author: PP-jessica
 * @Description:引入ChannelConfig配置类，这里面我删除了几个暂时还用不到的方法，等之后用到的时候再引入
 */
public interface ChannelConfig {

    Map<ChannelOption<?>, Object> getOptions();

    boolean setOptions(Map<ChannelOption<?>, ?> options);

    <T> T getOption(ChannelOption<T> option);

    <T> boolean setOption(ChannelOption<T> option, T value);

    int getConnectTimeoutMillis();

    ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    int getWriteSpinCount();

    ChannelConfig setWriteSpinCount(int writeSpinCount);

    boolean isAutoRead();

    ChannelConfig setAutoRead(boolean autoRead);

    /**
     * @Author: PP-jessica
     * @Description:内存分配器终于添加进来了
     */
    ByteBufAllocator getAllocator();


    ChannelConfig setAllocator(ByteBufAllocator allocator);

    /**
     * @Author: PP-jessica
     * @Description:动态内存分配器终于也添加进来了
     */
    <T extends RecvByteBufAllocator> T getRecvByteBufAllocator();

    ChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    boolean isAutoClose();

    ChannelConfig setAutoClose(boolean autoClose);

    int getWriteBufferHighWaterMark();

    ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    int getWriteBufferLowWaterMark();

    ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

}
