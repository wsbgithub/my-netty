package lessonthree;


public class Test {

    public static void main(String[] args) {
//        EventLoopGroup carEventLoopGroup = new CarEventLoopGroup(4);
//        Bootstrap bootstrap = new Bootstrap(carEventLoopGroup);
//        bootstrap.fireAndTakeGirlHome();

        CarEventLoopGroup carEventLoopGroup = new CarEventLoopGroup(4);
        carEventLoopGroup.takeGirlHome();
        //carEventLoopGroup.shutdownGracefully();
    }
}
