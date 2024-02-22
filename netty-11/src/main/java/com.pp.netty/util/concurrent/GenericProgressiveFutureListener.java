package com.pp.netty.util.concurrent;


public interface GenericProgressiveFutureListener<F extends ProgressiveFuture<?>> extends GenericFutureListener<F> {

    void operationProgressed(F future, long progress, long total) throws Exception;
}