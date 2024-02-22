package com.pp.netty.channel;

import com.pp.netty.util.ReferenceCounted;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

public interface FileRegion extends ReferenceCounted {


    long position();


    @Deprecated
    long transfered();


    long transferred();


    long count();


    long transferTo(WritableByteChannel target, long position) throws IOException;

    @Override
    FileRegion retain();

    @Override
    FileRegion retain(int increment);

    @Override
    FileRegion touch();

    @Override
    FileRegion touch(Object hint);
}
