package com.pp.netty.buffer;

public interface PoolChunkListMetric extends Iterable<PoolChunkMetric>{

    int minUsage();


    int maxUsage();
}
