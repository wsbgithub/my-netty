package lessonthree;

import java.util.concurrent.TimeUnit;

public abstract class MultiDriverEventExecutorGroup implements EventExecutorGroup{

    private EventExecutor[] eventExecutor;

    private int index = 0;

    public MultiDriverEventExecutorGroup(int drivers) {
        eventExecutor = new EventExecutor[drivers];
        for (int i = 0; i < drivers; i ++){
            eventExecutor[i] =  newChild();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这里定义了一个抽象方法。是给子类实现的。因为你不知道要返回的是哪种类型的EventExecutor。
     */
    protected abstract EventExecutor newChild();

    @Override
    public EventExecutor next() {
        int id = index % eventExecutor.length;
        index++;
        return eventExecutor[id];
    }

    @Override
    public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        next().shutdownGracefully(quietPeriod, timeout, unit);
    }
}
