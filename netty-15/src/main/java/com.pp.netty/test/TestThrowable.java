package com.pp.netty.test;

import com.pp.netty.util.internal.SocketUtils;

public class TestThrowable {

    public static void main(String[] args) {

        Throwable throwable = new Throwable();
        //得到调用栈
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        System.out.println("StackTraceElement数组的长度为====="+stackTrace.length);
        for (int i = 0; i <stackTrace.length ; i++) {
            //得到类的调用轨迹
            System.out.println(stackTrace[i].getClassName());
            //得到方法的调用轨迹
            System.out.println(stackTrace[i].getMethodName());
        }

        People people = new People();
        people.eat();
    }

}



class People {

    public void eat() {
        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("StackTraceElement数组的长度为====="+stackTrace.length);
        for (int i = 0; i <stackTrace.length ; i++) {
            System.out.println(stackTrace[i].getClassName());
            System.out.println(stackTrace[i].getMethodName());
            System.out.println();
        }
        sleep();
    }


    public void sleep() {
        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("StackTraceElement数组的长度为====="+stackTrace.length);
        for (int i = 0; i <stackTrace.length ; i++) {
            System.out.println(stackTrace[i].getClassName());
            System.out.println(stackTrace[i].getMethodName());
            System.out.println();
        }
        like();
    }


    public void like() {
        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        System.out.println("StackTraceElement数组的长度为====="+stackTrace.length);
        for (int i = 0; i <stackTrace.length ; i++) {
            System.out.println(stackTrace[i].getClassName());
            System.out.println(stackTrace[i].getMethodName());
            System.out.println();
        }
    }
}

