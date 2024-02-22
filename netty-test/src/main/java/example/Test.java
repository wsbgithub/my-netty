package example;

public class Test {

    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        CarEventLoopGroup workerGroup = new CarEventLoopGroup(2);
        bootstrap.group(workerGroup).fireAndTakeGirlHome();
    }
}
