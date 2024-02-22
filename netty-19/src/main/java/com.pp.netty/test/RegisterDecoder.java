package com.pp.netty.test;

import java.util.List;

/**
 * @Author: PP-jessica
 * @Description:自定义的解码器
 */
//public class RegisterDecoder extends ByteToMessageDecoder implements RegisterCodec {
//    @Override
//    public final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
//        //读取消息头数据，如果可读取的字节长度不够就直接返回，等待下一次数据
//        if (in.readableBytes() < RegisterConstants.HEADER_TOTAL_LEN) {
//            return;
//        }
//        //标记读到的位置，这时候要从0开始读了
//        in.markReaderIndex();
//        //读取魔数并验证
//        short magic = in.readShort();
//        if (magic != RegisterConstants.MAGIC) {
//            throw new IllegalArgumentException("magic number is illegal, " + magic);
//        }
//        //读取消息类型，状态和id
//        byte msgType = in.readByte();
//        byte status = in.readByte();
//        long requestId = in.readLong();
//        //读取序列化类型
//        byte serializationType = in.readByte();
//        //得到消息体的长度
//        int dataLength = in.readInt();
//        //剩下的可读字节长度小于消息体长度就返回，等待下一次数据
//        if (in.readableBytes() < dataLength) {
//            //可读的剩余字节小于字节长度的值，就回滚到之前的标记处，重新等待足够的字节到来
//            in.resetReaderIndex();
//            return;
//        }
//        byte[] data = new byte[dataLength];
//        in.readBytes(data);
//}