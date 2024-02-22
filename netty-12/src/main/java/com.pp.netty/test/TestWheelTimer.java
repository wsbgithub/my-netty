package com.pp.netty.test;

import com.pp.netty.util.HashedWheelTimer;
import com.pp.netty.util.Timeout;
import com.pp.netty.util.TimerTask;
import com.pp.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.TimeUnit;

public class TestWheelTimer {

    public static void main(String[] args) {
        HashedWheelTimer timer = new HashedWheelTimer(new DefaultThreadFactory("时间轮",false,5), 1, TimeUnit.SECONDS,8);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println("1秒执行1次");
                timer.newTimeout(this, 2, TimeUnit.SECONDS);
            }
        };
        timer.newTimeout(timerTask, 0, TimeUnit.SECONDS);
    }
}
