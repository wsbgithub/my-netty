package com.pp.netty.test;

import com.sun.corba.se.impl.orbutil.concurrent.Sync;

import java.util.concurrent.FutureTask;

public interface RunnableFuture<V> extends Runnable,Future<V> {

    @Override
    void run();
}
