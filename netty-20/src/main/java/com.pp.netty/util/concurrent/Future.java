package com.pp.netty.util.concurrent;

import java.util.concurrent.TimeUnit;


/**
 * @Author: PP-jessica
 * @Description:netty中重写了该接口，添加了一些重要的方法
 */
public interface Future<V> extends java.util.concurrent.Future<V> {


    boolean isSuccess();


    boolean isCancellable();


    Throwable cause();


    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);


    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);


    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);


    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);


    Future<V> sync() throws InterruptedException;


    Future<V> syncUninterruptibly();


    Future<V> await() throws InterruptedException;


    Future<V> awaitUninterruptibly();


    boolean await(long timeout, TimeUnit unit) throws InterruptedException;


    boolean await(long timeoutMillis) throws InterruptedException;


    boolean awaitUninterruptibly(long timeout, TimeUnit unit);


    boolean awaitUninterruptibly(long timeoutMillis);


    V getNow();

    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
