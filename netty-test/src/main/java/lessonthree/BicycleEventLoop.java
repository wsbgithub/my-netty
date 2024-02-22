package lessonthree;

public class BicycleEventLoop extends OneDriverEventLoop{

    private int speedPerHour;

    public BicycleEventLoop() {

    }

    public BicycleEventLoop(int speedPerHour) {
        this.speedPerHour = speedPerHour;
    }

    private void keepRunning() {
        System.out.println("我是自行车，我的的时速是" + speedPerHour +"km/h");
    }

    @Override
    protected void drive() {
        for (; ; ) {
            keepRunning();
        }
    }
}

