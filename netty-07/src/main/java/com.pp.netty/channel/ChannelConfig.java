package com.pp.netty.channel;

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
    //这个水位线暂时还用不到，等后面重构发送数据方法时，我们会真正用到的
    int getWriteSpinCount();

    ChannelConfig setWriteSpinCount(int writeSpinCount);

    boolean isAutoRead();

    ChannelConfig setAutoRead(boolean autoRead);

    boolean isAutoClose();

    ChannelConfig setAutoClose(boolean autoClose);

    int getWriteBufferHighWaterMark();

    ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    int getWriteBufferLowWaterMark();

    ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

}
