package com.pp.netty.test.runnable;

import java.util.concurrent.TimeUnit;


public class ScheduledFutureTask implements Runnable{

    //任务的创建时间，当第一个任务被创建的时候，随着ScheduledFutureTask类的初始化，该属性也被初始化了，并且只初始化一次
    private static final long START_TIME = System.nanoTime();

    //当前时间减去开始时间，得到到当前时间为止，距离第一个任务启动已经过去了多少时间
    static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    static long deadlineNanos(long delay) {
        //如果我的第一个定时任务是在第1秒执行的，也就是开始时间是1秒，现在是3秒，那就是3-1+5等于7，
        //也就是说用户提交的定时任务要在距离开始时间过去了7秒的时候执行
        //由此可见，这个时间并不是一个固定的刻度，而是距离第一个定时任务开始过去了多久
        //如果用户提交的一个定时任务要在距离开始时间5秒后执行，但现在System.nanoTime() - START_TIME=6，过去了6秒
        //那这个定时任务的执行时间就过去了，但是还没执行
        long deadlineNanos = nanoTime() + delay;
        //如果计算出错，就用下面的方法给deadlineNanos赋值
        return deadlineNanos;
    }

    //这个就是该任务要被执行的时间差
    private long deadlineNanos;


    public ScheduledFutureTask(long delay, TimeUnit unit){
        this.deadlineNanos = deadlineNanos(unit.toNanos(delay));
    }

    //获取定时任务的执行时间
    public long deadlineNanos() {
        return deadlineNanos;
    }

    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName()+":我现在要去看电影！");
    }
}
