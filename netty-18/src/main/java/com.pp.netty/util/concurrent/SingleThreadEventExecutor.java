package com.pp.netty.util.concurrent;

import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;


/**
 * @Author: PP-jessica
 * @Description:单线程执行器，实际上这个类就是一个单线程的线程池，netty中所有任务都是被该执行器执行的
 * 既然是执行器(虽然该执行器中只有一个无限循环的线程工作)，但执行器应该具备的属性也不可少，比如任务队列，拒绝策略等等
 * 暂时让该类最为执行器的顶层类,在源码中该类还有两层继承的类
 */
public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements EventExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);
    //执行器的初始状态，未启动
    private static final int ST_NOT_STARTED = 1;

    //执行器启动后的状态
    private static final int ST_STARTED = 2;

    //正在准备关闭的状态，这时候还没有关闭，一切正常运行
    private static final int ST_SHUTTING_DOWN = 3;

    private volatile int state = ST_NOT_STARTED;
    //执行器的状态更新器,也是一个原子类，通过cas来改变执行器的状态值
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Integer.MAX_VALUE;

    private final Queue<Runnable> taskQueue;

    private volatile Thread thread;
    //创建线程的执行器
    private Executor executor;

    private  EventExecutorGroup parent;

    private  boolean addTaskWakesUp;

    private volatile boolean interrupted;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private long lastExecutionTime;

    private static final Runnable WAKEUP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, Queue<Runnable> taskQueue,
                                        RejectedExecutionHandler rejectedHandler) {
        //暂时在这里赋值
        this.parent = parent;
        this.addTaskWakesUp = addTaskWakesUp;
        this.executor = executor;
        this.taskQueue = ObjectUtil.checkNotNull(taskQueue, "taskQueue");
        rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
    }


    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<Runnable>(maxPendingTasks);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法在nioeventloop中实现，是真正执行轮询的方法
     */
    protected abstract void run();

    /**
     * @Author: PP-jessica
     * @Description:执行器执行任务
     */
    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //这里一定会返回false，因为是其他线程向单线程执行器中提交任务的
        boolean inEventLoop = inEventLoop(Thread.currentThread());
        //把任务提交到任务队列中
        addTask(task);
        //启动单线程执行器中的线程
        startThread();
        //这里还有一个这个操作，比较重要的，就是要调用子类NioEventLoop中的wakeup方法，唤醒selector
        //以便执行用户提交的任务
        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }

    private void startThread() {
        //暂时先不考虑特别全面的线程池状态，只关心线程是否已经启动
        //如果执行器的状态是未启动，就cas将其状态值变为已启动
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    doStartThread();
                    success = true;
                } finally {
                    //如果启动未成功，直接把状态值复原
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    private void doStartThread() {
        //这里的executor是ThreadPerTaskExecutor，runnable -> threadFactory.newThread(command).start()
        //threadFactory中new出来的thread就是单线程线程池中的线程，它会调用nioeventloop中的run方法，无限循环，直到资源被释放
        executor.execute(new Runnable() {
            @Override
            public void run() {
                //Thread.currentThread得到的就是正在执行任务的单线程执行器的线程，这里把它赋值给thread属性十分重要
                //暂时先记住这一点
                thread = Thread.currentThread();
                if (interrupted) {
                    thread.interrupt();
                }
                //线程开始轮询处理IO事件，父类中的关键字this代表的是子类对象，这里调用的是nioeventloop中的run方法
                SingleThreadEventExecutor.this.run();
                logger.info("单线程执行器的线程错误结束了！");
            }
        });
    }

    /**
     * @Author: PP-jessica
     * @Description:判断当前执行任务的线程是否是执行器的线程。这个方法至关重要，现在先有个印象
     */
    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * @Author: PP-jessica
     * @Description:判断任务队列中是否有任务
     */
    protected boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    private void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //如果添加失败，执行拒绝策略
        if (!offerTask(task)) {
            reject(task);
        }
    }


    final boolean offerTask(Runnable task) {
        return taskQueue.offer(task);
    }


    protected Runnable pollTask() {
        assert inEventLoop(Thread.currentThread());
        return pollTaskFrom(taskQueue);
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        for (;;) {
            Runnable task = taskQueue.poll();
            if (task == WAKEUP_TASK) {
                continue;
            }
            return task;
        }
    }


    private boolean fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        //从定时任务队列中取出即将到期执行的定时任务
        Runnable scheduledTask  = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            //把取出的定时任务方法普通任务队列中
            //当添加失败的时候，则把该任务重新放回定时任务队列中
            if (!taskQueue.offer(scheduledTask)) {
                scheduledTaskQueue().add((ScheduledFutureTask<?>) scheduledTask);
                return false;
            }
            scheduledTask  = pollScheduledTask(nanoTime);
        }
        return true;
    }


    protected Runnable peekTask() {
        assert inEventLoop(Thread.currentThread());
        return taskQueue.peek();
    }


    public int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * @Author: PP-jessica
     * @Description:执行所有任务
     */
    protected boolean runAllTasks() {
        assert inEventLoop(Thread.currentThread());
        boolean fetchedAll;
        boolean ranAtLeastOne = false;
        do {
            //把到期的定时任务从任务队列取出放到普通任务队列中
            fetchedAll = fetchFromScheduledTaskQueue();
            //执行任务队列中的任务，该方法返回true，则意味着至少执行了一个任务
            if (runAllTasksFrom(taskQueue)) {
                //给该变量赋值为true
                ranAtLeastOne = true;
            }
            //没有可执行的定时任务时，就退出该循环
        } while (!fetchedAll);
        if (ranAtLeastOne) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        //执行尾部队列任务，这里还暂不实现
        //afterRunningAllTasks();
        return ranAtLeastOne;
    }

    protected final boolean runAllTasksFrom(Queue<Runnable> taskQueue) {
        //从普通任务队列中拉取异步任务
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        for (;;) {
            //Reactor线程执行异步任务
            safeExecute(task);
            //执行完毕拉取下一个，如果是null，则直接返回
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return true;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个是新添加的方法
     * 传进来的参数就是执行用户提交的任务所限制的时间
     */
    protected boolean runAllTasks(long timeoutNanos) {
        //仍然是先通过下面这个方法，把定时任务添加到普通的任务队列中，这个方法会循环拉取
        //也就是说，可能会把很多定时任务拉取到普通任务队列中，直到无法拉取就结束
        fetchFromScheduledTaskQueue();
        //从普通的任务队列中获得第一个任务
        Runnable task = pollTask();
        //如果任务为null，直接退出即可
        if (task == null) {
            //注释掉就行
            //afterRunningAllTasks();
            return false;
        }
        //这里通过ScheduledFutureTask.nanoTime()方法计算出第一个定时任务开始执行到当前时间为止经过了多少时间
        //然后加上传进来的这个参数，也就是限制用户任务执行的时间，得到的其实就是一个执行用户任务的截止时间
        //也就是说，执行用户任务，只能执行到这个时间
        final long deadline = ScheduledFutureTask.nanoTime() + timeoutNanos;
        //这个变量记录已经执行了的任务的数量
        long runTasks = 0;
        //最后一次执行的时间
        long lastExecutionTime;
        //开始循环执行了
        for (;;) {
            //执行任务队列中的任务
            safeExecute(task);
            //执行任务数量加1
            runTasks ++;
            //十六进制的0x3F其实就是十进制63
            //其二进制为111111，下面这里做&运算，如果等于0说明，runTasks的二进制的低6位都为0
            //而64的二进制为1000000，也就是说，只有当runTasks到达64的时候，下面这个判断条件就成立了
            //这里其实也是做了一个均衡的处理，就是判断看执行了64个用户提交的任务时，看看用户任务
            //的截止时间是否到了，如果到达截止时间，就退出循环。
            if ((runTasks & 0x3F) == 0) {
                //得到最后一次执行完的时间
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                //这里判断是否超过限制的时间了
                if (lastExecutionTime >= deadline) {
                    //超过就退出循环，没超过就继续执行
                    break;
                }
            }
            //走到这里就是获取下一个任务
            task = pollTask();
            if (task == null) {
                //如果为null，给最后一次执行完的时间赋值
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                break;
            }
        }
        //注释掉就行
        //afterRunningAllTasks();
        //给最后一次执行完的时间赋值
        this.lastExecutionTime = lastExecutionTime;
        return true;
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    protected long delayNanos(long currentTimeNanos) {
        //得到第一个定时任务
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL;
        }
        //得到第一个定时任务的开始执行时间
        return scheduledTask.delayNanos(currentTimeNanos);
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    @UnstableApi
    protected long deadlineNanos() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return nanoTime() + SCHEDULE_PURGE_INTERVAL;
        }
        return scheduledTask.deadlineNanos();
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop || state == ST_SHUTTING_DOWN) {
            taskQueue.offer(WAKEUP_TASK);
        }
    }

    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    protected final void reject(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    @SuppressWarnings("unused")
    protected boolean wakesUpForTask(Runnable task) {
        return true;
    }

    /**
     * @Author: PP-jessica
     * @Description: 中断单线程执行器中的线程
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            interrupted = true;
        } else {
            //中断线程并不是直接让该线程停止运行，而是提供一个中断信号
            //也就是标记，想要停止线程仍需要在运行流程中结合中断标记来判断
            currentThread.interrupt();
        }
    }

    @Override
    public void shutdownGracefully() {

    }

    @Override
    @Deprecated
    public void shutdown() {

    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }


    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return isTerminated();
    }

}
