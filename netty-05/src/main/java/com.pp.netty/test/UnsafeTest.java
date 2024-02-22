package com.pp.netty.test;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeTest {

    private static  sun.misc.Unsafe unsafe = getUnsafe();


    private  static sun.misc.Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
