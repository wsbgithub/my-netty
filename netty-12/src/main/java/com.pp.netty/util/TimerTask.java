package com.pp.netty.util;

import java.util.concurrent.TimeUnit;

public interface TimerTask {

    void run(Timeout timeout) throws Exception;
}
