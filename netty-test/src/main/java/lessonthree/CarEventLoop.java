package lessonthree;

public class CarEventLoop extends OneDriverEventLoop {

    private int speedPerHour;

    public CarEventLoop() {

    }

    public CarEventLoop(int speedPerHour) {
        this.speedPerHour = speedPerHour;
    }

    private void keepRunning() {
        System.out.println("我是汽车，我的的时速是" + speedPerHour +"km/h");
    }

    @Override
    protected void drive() {
        for (; ; ) {
            keepRunning();
        }
    }
}
