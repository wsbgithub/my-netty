package com.pp.netty.test;

import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.buffer.PooledByteBuf;
import com.pp.netty.buffer.PooledByteBufAllocator;
import com.pp.netty.channel.SimpleChannelInboundHandler;

public class PooledByteBufAllocatorTest {

    public static void main(String[] args) {

        //直接创建一个池化的直接内存分配器，该分配器默认分配的都是直接内存，当然也可以分配堆内存，但一般不会走到那些分支中去
        PooledByteBufAllocator pooledAllocator = PooledByteBufAllocator.DEFAULT;
        //该ByteBufAllocator.DEFAULT方法实际上是在ChannelConfig中，源码中会用该方法获得内存分配器
        //得到的其实仍然是个直接内存分配器，因为Netty默认使用的就是直接内存
        PooledByteBufAllocator configPooledAllocator = (PooledByteBufAllocator) ByteBufAllocator.DEFAULT;
        //申请2KB的直接内存
        PooledByteBuf byteBuf = (PooledByteBuf) pooledAllocator.buffer(1024 * 2);
        System.out.println(byteBuf);
        System.out.println(byteBuf.getHandle());
        byteBuf.writeBytes("我爱你老婆".getBytes());
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        System.out.println(new String(bytes));
        byteBuf.release();
        System.out.println(byteBuf.readableBytes());

        //用另一种方式申请2KB的直接内存
        PooledByteBuf byteBuf1 = (PooledByteBuf)configPooledAllocator.directBuffer(1024 *2);
        System.out.println(byteBuf1);
        System.out.println(byteBuf1.getHandle());
        System.out.println(byteBuf1.readableBytes());
        byteBuf1.release();


    }
}
