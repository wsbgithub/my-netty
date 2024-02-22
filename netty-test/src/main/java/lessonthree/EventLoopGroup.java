package lessonthree;

public interface EventLoopGroup extends EventExecutorGroup{

    /**
     * @Author: PP-jessica
     * @Description:这里之所以重定义EventExecutorGroup中的方法，是为了把返回值为EventLoop的同名方法分发到EventLoop中
     * 在重定义的接口方法中，子类方法的返回值可以是父类返回值的子类。这里的next方法就会在OneDriverEventLoop中得到实现。
     * 而OneDriverEventLoop是实现了EventLoop接口的。我们最终要创建的始终是一个EventLoop接口的实现的类。
     */
    @Override
    EventLoop next();

    void takeGirlHome();
}
