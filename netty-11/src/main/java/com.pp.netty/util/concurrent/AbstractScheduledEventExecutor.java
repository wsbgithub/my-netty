package com.pp.netty.util.concurrent;

import com.pp.netty.util.internal.DefaultPriorityQueue;
import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.PriorityQueue;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * @Author: PP-jessica
 * @Description:定时任务的核心类
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    /**
     * @Author: PP-jessica
     * @Description:该成员变量是一个比较器，通过task的到期时间比较大小。谁的到期时间长谁就大
     */
    private static final Comparator<ScheduledFutureTask<?>> SCHEDULED_FUTURE_TASK_COMPARATOR =
            new Comparator<ScheduledFutureTask<?>>() {
                @Override
                public int compare(ScheduledFutureTask<?> o1, ScheduledFutureTask<?> o2) {
                    return o1.compareTo(o2);
                }
            };

    //定时任务队列
    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue;

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        super(parent);
    }

    protected static long nanoTime() {
        return ScheduledFutureTask.nanoTime();
    }

    /**
     * @Author: PP-jessica
     * @Description:得到存储定时任务的任务队列，可以看到其实现实际上是一个优先级队列
     */
    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            //这里把定义好的比较器SCHEDULED_FUTURE_TASK_COMPARATOR传进去了
            scheduledTaskQueue = new DefaultPriorityQueue<ScheduledFutureTask<?>>(
                    SCHEDULED_FUTURE_TASK_COMPARATOR, 11);
        }
        return scheduledTaskQueue;
    }

    private static boolean isNullOrEmpty(Queue<ScheduledFutureTask<?>> queue) {
        return queue == null || queue.isEmpty();
    }

    /**
     * @Author: PP-jessica
     * @Description:取消任务队列中的所有任务
     */
    protected void cancelScheduledTasks() {
        assert inEventLoop(Thread.currentThread());
        //得到任务队列
        PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return;
        }
        //把任务队列转换成数组
        final ScheduledFutureTask<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(new ScheduledFutureTask<?>[0]);
        //依次取消任务，该方法最终回调用到promise中
        for (ScheduledFutureTask<?> task: scheduledTasks) {
            task.cancelWithoutRemove(false);
        }
        //清空数组，实际上只是把size置为0了
        scheduledTaskQueue.clearIgnoringIndexes();
    }


    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }


    /**
     * @Author: PP-jessica
     * @Description:该方法用来获取即将可以执行的定时任务
     */
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop(Thread.currentThread());
        //得到任务队列
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        //从任务队列中取出首元素
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return null;
        }
        //如果首任务符合被执行的条件，就将该任务返回
        if (scheduledTask.deadlineNanos() <= nanoTime) {
            scheduledTaskQueue.remove();
            return scheduledTask;
        }
        return null;
    }

    /**
     * @Author: PP-jessica
     * @Description:距离下一个任务执行的时间
     */
    protected final long nextScheduledTaskNano() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        //获取任务队列的头元素
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return -1;
        }
        //用该任务的到期时间减去当前事件
        return Math.max(0, scheduledTask.deadlineNanos() - nanoTime());
    }

    final ScheduledFutureTask<?> peekScheduledTask() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (scheduledTaskQueue == null) {
            return null;
        }
        //获取头部元素
        return scheduledTaskQueue.peek();
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法会在NioEventLoop中被调用，用来判断是否存在已经到期了的定时任务。实际上就是得到定时任务队列中的首任务
     * 判断其是否可以被执行了
     */
    protected final boolean hasScheduledTasks() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= nanoTime();
    }

    /**
     * @Author: PP-jessica
     * @Description:提交普通的定时任务到任务队列中
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);
        return schedule(new ScheduledFutureTask<Void>(
                this, command, null, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    /**
     * @Author: PP-jessica
     * @Description:提交有返回值的定时任务到任务队列
     */
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(callable, "callable");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);
        return schedule(new ScheduledFutureTask<V>(
                this, callable, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    /**
     * @Author: PP-jessica
     * @Description:下面这两个方法和java的那两个方法功能一样。大家对比着来看就行
     * 简单来说就是该方法是以固定速率执行，而下面的方法则是固定速率延时执行
     * 比如说period为2，两个任务都是从0秒开始执行
     * 这个方法执行的时候，如果任务有延时，比如2秒结束却执行了5秒，那么第五秒的时候会立刻执行下一个任务
     * 下面的方法则是即便任务超时了，下次执行的时候也要加上间隔的两秒这个值
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }
        validateScheduled0(initialDelay, unit);
        validateScheduled0(period, unit);
        //在这里提交定时任务致任务队列
        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }


    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        validateScheduled0(initialDelay, unit);
        validateScheduled0(delay, unit);
        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    @SuppressWarnings("deprecation")
    private void validateScheduled0(long amount, TimeUnit unit) {
        validateScheduled(amount, unit);
    }


    @Deprecated
    protected void validateScheduled(long amount, TimeUnit unit) {
        // NOOP
    }

    /**
     * @Author: PP-jessica
     * @Description:向定时任务队列中添加任务
     */
    <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop(Thread.currentThread())) {
            scheduledTaskQueue().add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    scheduledTaskQueue().add(task);
                }
            });
        }
        return task;
    }

    /**
     * @Author: PP-jessica
     * @Description:从任务队列中移除一个任务
     */
    final void removeScheduled(final ScheduledFutureTask<?> task) {
        if (inEventLoop(Thread.currentThread())) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    removeScheduled(task);
                }
            });
        }
    }
}

