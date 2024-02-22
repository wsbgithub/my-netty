package example;

import java.util.concurrent.TimeUnit;

public interface EventExecutorGroup {

    EventExecutor next();

    void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);
}
