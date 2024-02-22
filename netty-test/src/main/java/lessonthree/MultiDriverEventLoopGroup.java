package lessonthree;

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