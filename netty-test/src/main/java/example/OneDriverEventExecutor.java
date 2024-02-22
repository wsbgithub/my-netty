package example;


import java.util.concurrent.TimeUnit;

public abstract class OneDriverEventExecutor implements EventExecutor{

    public void beginDrive() {
        drive();
    }

    protected abstract void drive();

   @Override
   public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {

   }
}
