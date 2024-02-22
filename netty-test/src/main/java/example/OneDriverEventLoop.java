package example;

public abstract class OneDriverEventLoop extends OneDriverEventExecutor implements EventLoop{


    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public void takeGirlHome() {
        OneDriverEventLoop.this.beginDrive();
        System.out.println("带美女回家");
    }
}
