package com.pp.netty.util.concurrent;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author: PP-jessica
 * @Description:循环组的接口,暂时先不继承ScheduledExecutorService接口了
 */
public interface EventExecutorGroup extends Executor {

    EventExecutor next();

    /**
     * @Author: PP-jessica
     * @Description:下面这三个方法暂时不实现，源码中并不在本接口中，这里只是为了不报错，暂时放在这里
     */
    void shutdownGracefully();

    boolean isTerminated();

    void awaitTermination(Integer integer, TimeUnit timeUnit) throws InterruptedException;
}
