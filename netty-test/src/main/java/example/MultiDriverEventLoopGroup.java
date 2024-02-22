package example;


import java.util.concurrent.Executor;

/**
 * @Author: PP-jessica
 * @Description:这个类在这里，继承了MultiDriverEventExecutorGroup，其作用跟
 * OneDriverEventLoop继承了OneDriverEventExecutor的作用是一样的。
 */
public abstract class MultiDriverEventLoopGroup extends MultiDriverEventExecutorGroup implements EventLoopGroup{

    public MultiDriverEventLoopGroup(int drivers) {
        super(drivers);
    }

    @Override
    protected abstract EventLoop newChild();

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public void takeGirlHome() {
        next().takeGirlHome();
    }
}
