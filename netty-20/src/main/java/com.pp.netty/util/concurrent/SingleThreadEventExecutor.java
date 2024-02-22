package com.pp.netty.util.concurrent;

import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.PlatformDependent;
import com.pp.netty.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    //该状态下用户不能再提交任务了，但是单线程执行器还会执行剩下的任务
    private static final int ST_SHUTDOWN = 4;
    //单线程执行器真正关闭
    private static final int ST_TERMINATED = 5;

    private volatile int state = ST_NOT_STARTED;

    //优雅关闭静默期
    private volatile long gracefulShutdownQuietPeriod;
    //优雅关闭超时时间
    private volatile long gracefulShutdownTimeout;
    //优雅关闭开始时间
    private long gracefulShutdownStartTime;


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
    private final CountDownLatch threadLock = new CountDownLatch(1);
    //这个成员变量很有意思，用户可以自己定义一些任务
    //当单线程执行器停止运行的时候，会执行这些任务，这里其实就是借鉴了jvm的钩子函数
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>();

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private long lastExecutionTime;

    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    private static final Runnable WAKEUP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };
    private static final Runnable NOOP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, Queue<Runnable> taskQueue,
                                        RejectedExecutionHandler rejectedHandler) {
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
        boolean inEventLoop = inEventLoop(Thread.currentThread());
        addTask(task);
        //这里得到的结果肯定为false，因为单线程执行器还未创建，thread还未被赋值，肯定为false
        //取反为true
        if (!inEventLoop) {
            //启动单线程执行器
            startThread();
            //如果单线程执行器的state >= ST_SHUTDOWN，这时候就意味着用户不能再提交任务了
            if (isShutdown()) {
                boolean reject = false;
                try {
                    //刚才添加了什么任务，这里就删除
                    //因为添加任务，也就是 addTask(task)方法内部是没有状态限制的
                    //所以添加了要再删除
                    if (removeTask(task)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                }
                if (reject) {
                    //这里抛出异常就是通知用户别再提交任务了
                    //可以看到，这里Netty其实没有设置特别强硬的拒绝任务的手段
                    reject();
                }
            }
        }
        //这里就是单线程执行器还在正常运转的状态
        //所以添加任务后要唤醒selector，防止线程阻塞，不能执行异步任务
        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }

    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return taskQueue.remove(task);
    }

    private void startThread() {
        //判断线程状态是不是还未启动的状态
        if (state == ST_NOT_STARTED) {
            //cas改变状态
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    //启动线程
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) {
                        //没成功则重置状态
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    private boolean ensureThreadStarted(int oldState) {
        if (oldState == ST_NOT_STARTED) {
            try {
                doStartThread();
            } catch (Throwable cause) {
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);

                if (!(cause instanceof Exception)) {
                    PlatformDependent.throwException(cause);
                }
                return true;
            }
        }
        return false;
    }

    protected void updateLastExecutionTime() {
        lastExecutionTime = ScheduledFutureTask.nanoTime();
    }


    private void doStartThread() {
        assert thread == null;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                thread = Thread.currentThread();
                if (interrupted) {
                    thread.interrupt();
                }
                boolean success = false;
                updateLastExecutionTime();
                try {
                    //真正的循环，也就是单线程执行器启动后要处理的IO时间
                    SingleThreadEventExecutor.this.run();
                    //如果走到这里，意味着循环结束了，也就意味着单线程执行器停止了
                    success = true;
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from an event executor: ", t);
                } finally {
                    //走到这里意味着单线程执行器结束循环了，并且任务队列中的任务和ShutdownHook也都执行完了
                    //具体逻辑可以看NioEventLoop类中run方法的逻辑
                    //这里就会最后对单线程执行器的状态进行变更，最终变更到ST_TERMINATED状态
                    for (;;) {
                        int oldState = state;
                        //先判断状态，如果状态已经大于等于ST_SHUTTING_DOWN，就直接退出循环
                        //如果前面返回为false，才会执行后面的代码，就把状态设置到大于等于ST_SHUTTING_DOWN
                        if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                                SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                            break;
                        }
                    }
                    //这里是为了确认有没有执行confirmShutdown，因为在confirmShutdown方法内，
                    //gracefulShutdownStartTime会被赋值的
                    if (success && gracefulShutdownStartTime == 0) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                    SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " +
                                    "be called before run() implementation terminates.");
                        }
                    }
                    try {
                        for (;;) {
                            //这里最后一次执行这个方法，是因为这时候单线程执行器的状态还未改变，用户提交的任务还可以被执行，此时状态
                            //还为大于等于ST_SHUTTING_DOWN的状态，但绝对不是ST_TERMINATED，这个状态是绝对关闭状态
                            //而处在3的状态，任务还是可以被执行的
                            if (confirmShutdown()) {
                                break;
                            }
                        }
                    } finally {
                        try {
                            //关闭selector
                            cleanup();
                        } finally {
                            //清除单线程执行器的本地缓存
                            FastThreadLocal.removeAll();
                            //单线程执行器的状态设置为终结状态
                            STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                            //使得awaitTermination方法返回
                            threadLock.countDown();
                            if (logger.isWarnEnabled() && !taskQueue.isEmpty()) {
                                logger.warn("An event executor terminated with " +
                                        //这里会统计一下任务队列中有多少为执行的任务，然后打印一下
                                        "non-empty task queue (" + taskQueue.size() + ')');
                            }
                            //设置promise为成功状态，之前为这个promise添加的监听器就会回调了
                            //MultithreadEventExecutorGroup中的terminationFuture
                            //就会被设置成功，用户就可以知道了
                            terminationFuture.setSuccess(null);
                        }
                    }
                }
            }
        });
    }

    protected void cleanup() {
        // NOOP
    }

    @Override
    public void shutdown() {

    }

    /**
     * @Author: PP-jessica
     * @Description:这个方法就是负责收拾尾声的方法，处理任务队列中的任务，执行用户提交的ShutdownHook
     * 当然，并不是无期限执行，也会根据超时时间来执行，超过超时时间，就会终止单线程执行器了
     */
    protected boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }
        if (!inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException("must be invoked from an event loop");
        }
        //首先把所有定时任务取消了
        cancelScheduledTasks();
        //获得优雅释放资源的开始时间
        if (gracefulShutdownStartTime == 0) {
            //在这里赋值了
            gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
        }
        //这里会执行所有任务
        //runShutdownHooks有点类似于jvm的钩子函数，都是程序退出时执行的
        //netty就借鉴过来了，改到释放资源时执行，因为这也相当于程序要退出了
        if (runAllTasks() || runShutdownHooks()) {
            if (isShutdown()) {
                //判断状态是否关闭了
                //为什么可以随时判断状态，这里要给大家再理一下思绪，因为我们是调用group的shutdownGracefully方法释放执行器的
                //这个方法是主线程在执行
                //而现在这个方法是单线程执行器在执行，两个并不缠在一起，界限很清晰，所以可以随时判断的
                return true;
            }
           //如果静默期为0，就可以直接退出了
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }
            //防止单线程执行器阻塞，这里会向单线程执行器中添加一个空任务
            //如果单线程执行器管理爹线程此刻正在run方法的死循环中执行select方法
            //并且一直没有就绪事件，任务队列中也没有任务，那就会在select方法中一直循环
            //这里添加一个空任务，就可以打破死循环，让单线程执行器继续向下执行了
            wakeup(true);
            return false;
        }
        //获得当前的纳秒时间
        final long nanoTime = ScheduledFutureTask.nanoTime();
        //判断是否超过了超时时间，如果超过直接关闭执行器
        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }
        //lastExecutionTime为上次任务的完成时间
        //这里就是没有超过静默期呢，所以不能关闭单线程执行器
        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            wakeup(true);
            try {
                //睡100ms检查一次，看在静默期中是否有任务要执行
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }
        //在静默期中没有任务要执行，直接结束即可
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }
        //在这里阻塞住了
        threadLock.await(timeout, unit);

        return isTerminated();
    }

    @Override
    public boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * @Author: PP-jessica
     * @Description:这里就是关闭或者说优雅释放单线程执行器的真正方法
     */
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (quietPeriod < 0) {
            throw new IllegalArgumentException("quietPeriod: " + quietPeriod + " (expected >= 0)");
        }
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (isShuttingDown()) {
            return terminationFuture();
        }
        //这里会返回false
        boolean inEventLoop = inEventLoop(Thread.currentThread());
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return terminationFuture();
            }
            int newState;
            //这个是为了唤醒selector，防止单线程执行器阻塞，一旦阻塞就执行不了剩下的任务了
            //一定要弄清楚，这个是在主线程中执行的
            wakeup = true;
            //得到此时单线程执行器的状态
            oldState = state;
            if (inEventLoop) {
                newState = ST_SHUTTING_DOWN;
            } else {
                //会走这个分支
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        //如果是前两个就设置为第3个
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        //走到这里就意味着是第3或第4，没必要改变状态，因为状态的最终改变是在
                        //单线程执行器的run方法中改变的
                        //这里状态改变只是为了让IO事件的循环被打破，好执行到单线程执行器的run方法中
                        newState = oldState;
                        wakeup = false;
                }
            }
            //cas修改状态，就是把上面得到的状态设置一下，因为上面设置的是新状态，oldState还未被更改呢
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                //修改成功则退出循环
                break;
            }
        }
        //静默期赋值，可以在confirmShutdown方法中使用
        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        //超时时间赋值，同样在confirmShutdown方法中使用
        gracefulShutdownTimeout = unit.toNanos(timeout);
        //这保证单线程执行器还在运行，如果单线程执行器在上面的cas中状态修改成功，就可以直接退出了
        //而不用再进行下面的代码
        if (ensureThreadStarted(oldState)) {
            return terminationFuture;
        }
        //这里仍然是进行一次selector的唤醒，单线程执行器去执行任务队列中的任务，因为要关闭了
        //这里要结合上面的switch分支来理解
        if (wakeup) {
            wakeup(inEventLoop);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    public void addShutdownHook(final Runnable task) {
        if (inEventLoop(Thread.currentThread())) {
            shutdownHooks.add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.add(task);
                }
            });
        }
    }


    public void removeShutdownHook(final Runnable task) {
        if (inEventLoop(Thread.currentThread())) {
            shutdownHooks.remove(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.remove(task);
                }
            });
        }
    }

    private boolean runShutdownHooks() {
        boolean ran = false;
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<Runnable>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task: copy) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.warn("Shutdown hook raised an exception.", t);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }

        return ran;
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
        //判断单线程执行器的状态，如果是关闭状态，就不能再提交任务了
        if (isShutdown()) {
            reject();
        }
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
                //如果为null，并且到达截止时间，就退出循环
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
            currentThread.interrupt();
        }
    }

}
