package lessonthree;

public class Bootstrap {

    private EventLoopGroup eventLoopGroup;
    //这里我们既可以传入CarEventLoopGroup，也可以传入BicycleEventLoop
    public Bootstrap(EventLoopGroup eventLoopGroup){
        this.eventLoopGroup = eventLoopGroup;
    }

    public void fireAndTakeGirlHome() {
        //很明显，大家会在这里看到一个报错，因为EventLoopGroup中根本就没有takeGirlHome方法
        eventLoopGroup.takeGirlHome();
    }
}
