package com.pp.netty.buffer;

public interface PoolChunkMetric {

    int usage();


    int chunkSize();


    int freeBytes();
}
