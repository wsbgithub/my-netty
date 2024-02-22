package com.pp.netty.util.internal;

import java.util.Queue;


/**
 * @Author: PP-jessica
 * @Description:定时任务队列的接口
 */
public interface PriorityQueue<T> extends Queue<T> {

    boolean removeTyped(T node);


    boolean containsTyped(T node);


    void priorityChanged(T node);


    void clearIgnoringIndexes();
}

