package lessonthree;

public class CarEventLoopGroup extends MultiDriverEventLoopGroup{

    public CarEventLoopGroup(int cars) {
        super(cars);
    }

    @Override
    protected EventLoop newChild(){
        return new CarEventLoop();
    }

}
