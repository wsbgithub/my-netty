package lessonthree;

public abstract class OneDriverEventLoop extends OneDriverEventExecutor implements EventLoop{


    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public void takeGirlHome() {
        System.out.println("带美女回家");
    }
}
