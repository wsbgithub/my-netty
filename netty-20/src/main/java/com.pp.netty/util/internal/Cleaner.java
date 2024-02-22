package com.pp.netty.util.internal;

import java.nio.ByteBuffer;

public interface Cleaner {

    void freeDirectBuffer(ByteBuffer buffer);
}
