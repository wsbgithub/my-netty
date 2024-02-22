package com.pp.netty.test;

import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.channel.ChannelHandlerContext;
import com.pp.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;



/**
 * 自定义编码器类，继承自Netty的MessageToByteEncoder类。
 * 使用Netty进行网络编程时，可以在ChannelPipeline中添加这个编码器，用于将发送的消息编码为字节流。
 */
public class StringToByteEncoder extends MessageToByteEncoder<String> {

    /**
     * 该方法用于将消息编码为字节流。
     *
     * @param ctx ChannelHandlerContext对象，提供了许多操作Channel的方法
     * @param msg 需要编码的消息
     * @param out 编码后的字节流将被写入这个ByteBuf对象
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        // 将字符串消息转换为字节流，这里使用了UTF-8编码
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);

        // 将字节流写入ByteBuf对象中，这样Netty就可以将其发送出去
        out.writeBytes(bytes);
    }
}