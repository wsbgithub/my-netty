package com.pp.netty.util.concurrent;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * @Author: PP-jessica
 * @Description:执行器选择工厂的实现类
 */
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        //如果数组的长度是2的幂次方就返回PowerOfTwoEventExecutorChooser选择器
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            //如果数组长度不是2的幂次方就返回通用选择器。其实看到2的幂次方，应该就可以想到作者考虑的是位运算，hashmap中是不是也有相同的
            //思想呢？计算数据下标的时候。
            return new GenericEventExecutorChooser(executors);
        }
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            //idx初始化为0，之后每一次调用next方法，idx都会自增，这样经过运算后，得到的数组下标就会成为一个循环，执行器也就会被循环获取
            //也就是轮询
            int index = idx.getAndIncrement() & executors.length - 1;
            System.out.println("客户端channel注册了第"+index +"workgroup中的执行器！");
            return executors[index];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
