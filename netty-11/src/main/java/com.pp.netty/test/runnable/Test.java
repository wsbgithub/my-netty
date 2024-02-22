package com.pp.netty.test.runnable;


import java.util.Timer;
import java.util.concurrent.TimeUnit;


public class Test {

    public static void main(String[] args) throws InterruptedException {
        //创建一个定时任务
        ScheduledFutureTask task = new ScheduledFutureTask(5000, TimeUnit.NANOSECONDS);
        //在这里循环判断当前时间减去程序开始时间的差值，是否大于定时任务要执行的时间差值，如果小于，说明还不到执行时间
        //就在循环里睡一会，如果大于，说明定时任务该执行了。
        //就比如说定时任务要在程序开始时间5秒之后执行，而现在的时间减去程序开始时间的差值是6秒，说明已经过去6秒了
        //已经超过定时任务要执行的时间差值了，定时任务就该马上执行了
        while (ScheduledFutureTask.nanoTime() <= task.deadlineNanos()) {
            //睡一会
            TimeUnit.NANOSECONDS.sleep(1000);
        }
        //执行定时任务
        task.run();
    }

}
