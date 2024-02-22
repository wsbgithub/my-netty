package example;

public class Bootstrap {

    private EventLoopGroup eventLoopGroup;

    public Bootstrap() {

    }

    public Bootstrap(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
    }

    public Bootstrap group(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public void fireAndTakeGirlHome() {
        eventLoopGroup.takeGirlHome();
    }
}
