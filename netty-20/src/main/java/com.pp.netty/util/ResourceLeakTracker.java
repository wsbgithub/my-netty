package com.pp.netty.util;

public interface ResourceLeakTracker<T>  {


    void record();


    void record(Object hint);


    boolean close(T trackedObject);
}
