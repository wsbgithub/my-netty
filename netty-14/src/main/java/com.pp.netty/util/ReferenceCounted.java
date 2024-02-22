package com.pp.netty.util;


public interface ReferenceCounted {

    int refCnt();


    ReferenceCounted retain();


    ReferenceCounted retain(int increment);


    ReferenceCounted touch();

    ReferenceCounted touch(Object hint);


    boolean release();


    boolean release(int decrement);
}
