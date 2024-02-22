package com.pp.netty.test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * @Author: PP-jessica
 * @Description:位运算测试类
 */
public class Test {
    public static void main(String[] args) throws InterruptedException {
        // 创建一个 ReferenceQueue 对象
        ReferenceQueue<Peopel> referenceQueue = new ReferenceQueue<>();

        // 创建一个弱引用对象，关联到 MyObject，并传入 ReferenceQueue 对象
        Peopel myObject = new Peopel();
        WeakReference<Peopel> weakReference = new WeakReference<>(myObject, referenceQueue);


        // 立即将 myObject 设置为 null，以便触发垃圾回收
        myObject = null;
        weakReference.clear();

        // 执行垃圾回收（这里仅为示例，实际情况下无需显式调用）
        System.gc();

        //Reference<? extends Peopel> reference = referenceQueue.remove();
        Reference<? extends Peopel> reference = referenceQueue.poll();
        System.out.println(reference);
        System.out.println(reference.get());

    }

    static class Peopel {

    }

}
