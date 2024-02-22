package example;

import java.util.concurrent.Executor;

public class CarEventLoopGroup extends MultiDriverEventLoopGroup{

    private CarEventLoop[] carEventLoops;

    private int index = 0;

    public CarEventLoopGroup(int cars) {
        super(cars);
    }

    @Override
    protected EventLoop newChild(){
        return new CarEventLoop();
    }

}
