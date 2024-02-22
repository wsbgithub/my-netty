package com.pp.netty.util;


@Deprecated
public interface ResourceLeak {

    void record();


    void record(Object hint);


    boolean close();
}