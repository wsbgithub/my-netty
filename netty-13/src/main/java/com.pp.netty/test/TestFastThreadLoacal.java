package com.pp.netty.test;


import com.pp.netty.util.concurrent.FastThreadLocal;
import com.pp.netty.util.concurrent.FastThreadLocalThread;

public class TestFastThreadLoacal {

    public static void main(String[] args) throws InterruptedException {
        //首先创建一个runnable
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                FastThreadLocal<String> fastThreadLocal = new FastThreadLocal<>();
                fastThreadLocal.set("heihei");
                String str = fastThreadLocal.get();
                System.out.println(str);
            }
        };
        //因为FastThreadLocal必须配合FastThreadLocalThread来使用
        //所以我们的程序必须在FastThreadLocalThread线程中执行
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread(runnable);
        fastThreadLocalThread.start();
    }
}
