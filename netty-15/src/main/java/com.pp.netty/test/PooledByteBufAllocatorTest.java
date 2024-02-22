package com.pp.netty.test;

import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.buffer.PooledByteBufAllocator;

import java.lang.Throwable;



public class PooledByteBufAllocatorTest {

    public static void main(String[] args){

        //直接创建一个池化的直接内存分配器，该分配器默认分配的都是直接内存，当然也可以分配堆内存，但一般不会走到那些分支中去
        PooledByteBufAllocator pooledAllocator = PooledByteBufAllocator.DEFAULT;

        //申请2KB的直接内存
        for (int i = 0; i <500 ; i++) {
            ByteBuf buffer = pooledAllocator.buffer(1024 * 2);
            buffer.writeByte(1);
            buffer.writeShort(1);
            buffer = null;
            System.gc();
        }

    }
}
