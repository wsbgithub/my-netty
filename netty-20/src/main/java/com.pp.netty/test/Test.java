package com.pp.netty.test;

/**
 * @Author: PP-jessica
 * @Description:位运算测试类
 */
public class Test {
    public static void main(String[] args) {
        int i = reqCapacity(1022);
        System.out.println(i);
    }

    public static int reqCapacity(int reqCapacity) {

        int normalizedCapacity = reqCapacity;
        normalizedCapacity --;
        normalizedCapacity = normalizedCapacity | normalizedCapacity >>>  1;
        normalizedCapacity = normalizedCapacity | normalizedCapacity >>>  2;
        normalizedCapacity = normalizedCapacity | normalizedCapacity >>>  4;
        normalizedCapacity = normalizedCapacity | normalizedCapacity >>>  8;
        normalizedCapacity = normalizedCapacity | normalizedCapacity >>> 16;
        normalizedCapacity ++;
        return normalizedCapacity;
    }
}
