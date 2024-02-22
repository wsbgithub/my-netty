package lessonthree;

import java.util.concurrent.TimeUnit;

public abstract class OneDriverEventExecutor implements EventExecutor{

    protected abstract void drive();

    @Override
    public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {

    }
}
