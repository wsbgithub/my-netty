package com.pp.netty.buffer;



/**
 * @Author: PP-jessica
 * @Description:决定使用直接内存还是堆内存
 */
public interface ByteBufAllocatorMetric {

    long usedHeapMemory();


    long usedDirectMemory();
}
