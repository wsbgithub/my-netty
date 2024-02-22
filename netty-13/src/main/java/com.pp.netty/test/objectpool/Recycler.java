package com.pp.netty.test.objectpool;

public abstract class Recycler<T> {
    //先定义一个存放对象的数组容器
    private final Object[] elements;

    //数组的初始化容量，默认为256
    private  int INITIAL_CAPACITY = 256;

    //数组存储元素的个数
    private int size;

    //无参构造器
    public Recycler() {
        //在这里把数组创建好
        elements = new Object[INITIAL_CAPACITY];
    }

    //从对象池中获取对象
    public T pop() {
        synchronized (elements) {
            if (size == 0) {
                //走到这里说明对象池中还没有对象，所以就创建一个对象，直接返回
                return newObject();
            }
            //走到这里说明对象池中已经有对象了，可以直接返回对象了
            //这一次从数组的尾部取走元素，从尾部获取元素可以避免数组中的数据的频繁移动
            T object = (T)elements[--size];
            //把对象池中的数组置为空
            elements[size] = null;
            return object;
        }
    }

    //抽象方法
    protected abstract T newObject();

    //将对象归还给对象池
    public void push(T object) {
        synchronized (elements) {
            //把对象归还到对象池中
            elements[size++] = object;
        }
    }
}
