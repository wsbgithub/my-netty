package com.pp.netty.channel;

import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.util.*;
import com.pp.netty.util.concurrent.EventExecutor;
import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.StringUtil;
import com.pp.netty.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static com.pp.netty.channel.ChannelHandlerMask.*;

/**
 * @Author: PP-jessica
 * @Description:该类和DefaultChannelPipeline类似，引入了很多方法，
 * 也都是直接从源码中复制过来的，很多方法不需要做改动，直接加上注释就行
 */
abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    private static final Logger logger = LoggerFactory.getLogger(AbstractChannelHandlerContext.class);
    /**
     * @Author: PP-jessica
     * @Description:链表的前一个节点和后一个节点
     */
    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;
    /**
     * @Author: PP-jessica
     * @Description:ChannelHandler的状态原子更新器
     */
    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractChannelHandlerContext.class, "handlerState");

    /**
     * @Author: PP-jessica
     * @Description；之前讲到的ChannelHandler添加到ChannelPipeline链表中时，会有一个添加状态，只有状态为ADD_COMPLETE
     * 的handler才能处理数据
     */
    private static final int ADD_PENDING = 1;
    private static final int ADD_COMPLETE = 2;
    private static final int REMOVE_COMPLETE = 3;
    /**
     * @Author: PP-jessica
     * @Description:ChannelHandler添加链表后的初始状态
     */
    private static final int INIT = 0;
    /**
     * @Author: PP-jessica
     * @Description:ChannelPipeline可以得到每一个ChannelHandler，而每一个封装着ChannelHandler的ChannelHandlerContext又可以得到
     * 自己所在的链表
     */
    private final DefaultChannelPipeline pipeline;
    /**
     * @Author: PP-jessica
     * @Description:ChannelHandler所对应的名字
     */
    private final String name;
    /**
     * @Author: PP-jessica
     * @Description:该值为false，ChannelHandler状态为ADD_PENDING的时候，也可以响应pipeline中的事件
     * 该值为true表示只有ChannelHandler的状态为ADD_COMPLETE时，才能响应pipeline中的事件
     */
    private final boolean ordered;
    /**
     * @Author: PP-jessica
     * @Description:这是个很有意思的属性，变量名称为执行掩码，看名字肯定一头雾水，用起来却很有意思。
     * 试想一下，也许我们会向ChannelPipeline中添加很多handler，每个handler都有channelRead，如果有的handler并不对read事件感兴趣，
     * 数据在链表中传递的时候，就会自动跳过该handler。这个掩码，就是表明该handler对哪个事件感兴趣的
     */
    private final int executionMask;

    final EventExecutor executor;
    private ChannelFuture succeededFuture;

    /**
     * @Author: PP-jessica
     * @Description:这个是新添加的属性，是给多个方法内部使用的，要将发送消息封装为异步任务时，就会用到这个属性了
     * 具体逻辑可以去看看该类的flush方法内的逻辑
     */
    private Tasks invokeTasks;
    /**
     * @Author: PP-jessica
     * @Description:把初始状态赋值给handlerState，handlerState属性就是ChannelHandler刚添加到链表时的状态
     */
    private volatile int handlerState = INIT;

    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor,
                                  String name, Class<? extends ChannelHandler> handlerClass) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.pipeline = pipeline;
        this.executor = executor;
        //channelHandlerContext中保存channelHandler的执行条件掩码
        this.executionMask = mask(handlerClass);
        // Its ordered if its driven by the EventLoop or the given Executor is an instanceof OrderedEventExecutor.
        ordered = executor == null;
    }

    @Override
    public Channel channel() {
        return pipeline.channel();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法引入了
     */
    public ByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public EventExecutor executor() {
        if (executor == null) {
            return channel().eventLoop();
        } else {
            return executor;
        }
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * @Author: PP-jessica
     * @Description:找到下一个对registere事件感兴趣的ChannelHandler，registere事件就是handler中的channelRegistered方法，
     * 只要该方法被重写，就意味着该ChannelHandler对registere事件感兴趣。
     */
    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        invokeChannelRegistered(findContextInbound(MASK_CHANNEL_REGISTERED));
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:执行该handler中的ChannelRegistered方法，从该方法可以看出，一旦channel绑定了单线程执行器，
     * 那么关于该channel的一切，都要由单线程执行器来执行和处理。如果当前调用方法的线程不是单线程执行器的线程，那就
     * 把要进行的动作封装为异步任务提交给执行器
     */
    static void invokeChannelRegistered(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRegistered();
                }
            });
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:真正执行handler中的ChannelRegistered方法
     */
    private void invokeChannelRegistered() {
        //接下来会一直看见invokeHandler这个方法，这个方法就是判断CannelHandler在链表中的状态，只有是ADD_COMPLETE，
        //才会返回true，方法才能继续向下运行，如果返回false，那就进入else分支，会跳过该节点，寻找下一个可以处理数据的节点
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelRegistered(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRegistered();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:有了上面的那个例子，下面的这些方法就都不再解释了，逻辑都是一样的，无非就是ChannelHandler感兴趣
     * 的事情变了。比如说这个，感情去的就是Unregistered事件，也就意味着该ChannelHandler肯定重写了channelUnregistered方法
     */
    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        invokeChannelUnregistered(findContextInbound(MASK_CHANNEL_UNREGISTERED));
        return this;
    }

    static void invokeChannelUnregistered(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelUnregistered();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelUnregistered();
                }
            });
        }
    }

    private void invokeChannelUnregistered() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelUnregistered(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelUnregistered();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        invokeChannelActive(findContextInbound(MASK_CHANNEL_ACTIVE));
        return this;
    }

    static void invokeChannelActive(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelActive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelActive();
                }
            });
        }
    }

    private void invokeChannelActive() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelActive(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelActive();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        invokeChannelInactive(findContextInbound(MASK_CHANNEL_INACTIVE));
        return this;
    }

    static void invokeChannelInactive(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelInactive();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelInactive();
                }
            });
        }
    }

    private void invokeChannelInactive() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelInactive(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelInactive();
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        invokeExceptionCaught(findContextInbound(MASK_EXCEPTION_CAUGHT), cause);
        return this;
    }

    static void invokeExceptionCaught(final AbstractChannelHandlerContext next, final Throwable cause) {
        ObjectUtil.checkNotNull(cause, "cause");
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeExceptionCaught(cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        next.invokeExceptionCaught(cause);
                    }
                });
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to submit an exceptionCaught() event.", t);
                    logger.warn("The exceptionCaught() event that was failed to submit was:", cause);
                }
            }
        }
    }

    private void invokeExceptionCaught(final Throwable cause) {
        if (invokeHandler()) {
            try {
                handler().exceptionCaught(this, cause);
            } catch (Throwable error) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "An exception {}" +
                                    "was thrown by a user handler's exceptionCaught() " +
                                    "method while handling the following exception:",
                            //ThrowableUtil.stackTraceToString(error),
                            cause);
                } else if (logger.isWarnEnabled()) {
                    logger.warn(
                            "An exception '{}' [enable DEBUG level for full stacktrace] " +
                                    "was thrown by a user handler's exceptionCaught() " +
                                    "method while handling the following exception:", error, cause);
                }
            }
        } else {
            fireExceptionCaught(cause);
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(final Object event) {
        invokeUserEventTriggered(findContextInbound(MASK_USER_EVENT_TRIGGERED), event);
        return this;
    }

    static void invokeUserEventTriggered(final AbstractChannelHandlerContext next, final Object event) {
        ObjectUtil.checkNotNull(event, "event");
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeUserEventTriggered(event);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeUserEventTriggered(event);
                }
            });
        }
    }

    private void invokeUserEventTriggered(Object event) {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).userEventTriggered(this, event);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireUserEventTriggered(event);
        }
    }


    /**
     * @Author: PP-jessica
     * @Description:重头方法，我们写代码的时候，写一个rpc框架，消息中间件等等，肯定要用到handler的这个方法
     * 都会重写ChannelRead方法
     */
    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        invokeChannelRead(findContextInbound(MASK_CHANNEL_READ), msg);
        return this;
    }

    static void invokeChannelRead(final AbstractChannelHandlerContext next, Object msg) {
        final Object m = next.pipeline.touch(ObjectUtil.checkNotNull(msg, "msg"), next);
                EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelRead(m);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRead(m);
                }
            });
        }
    }

    private void invokeChannelRead(Object msg) {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelRead(this, msg);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRead(msg);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        invokeChannelReadComplete(findContextInbound(MASK_CHANNEL_READ_COMPLETE));
        return this;
    }

    static void invokeChannelReadComplete(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelReadComplete();
        }
        //因为Tasks引入进来了，所以这里也可以放开了
        else {
            Tasks tasks = next.invokeTasks;
            if (tasks == null) {
                next.invokeTasks = tasks = new Tasks(next);
            }
            executor.execute(tasks.invokeChannelReadCompleteTask);
        }
    }

    private void invokeChannelReadComplete() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelReadComplete(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelReadComplete();
        }
    }


    /**
     * @Author: PP-jessica
     * @Description:下面这几个方法还是很重要的
     */
    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        invokeChannelWritabilityChanged(findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED));
        return this;
    }

    static void invokeChannelWritabilityChanged(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelWritabilityChanged();
        }
        else {
            Tasks tasks = next.invokeTasks;
            if (tasks == null) {
                next.invokeTasks = tasks = new Tasks(next);
            }
            executor.execute(tasks.invokeChannelWritableStateChangedTask);
        }
    }

    private void invokeChannelWritabilityChanged() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelWritabilityChanged(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelWritabilityChanged();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这几个方法应该很熟悉了吧，都是出站处理器中的方法，这时候大家也应该明白了，
     * 每当我调用一个方法时，比如说就是服务端channel的绑定端口号的bind方法，调用链路会先从AbstractChannel类中开始，
     * 但是，channel拥有ChannelPipeline链表，链表中有一系列的处理器，所以调用链就会跑到ChannelPipeline中，然后从ChannelPipeline
     * 又跑到每一个ChannelHandler中，经过这些ChannelHandler的处理，调用链又会跑到channel的内部类Unsafe中，再经过一系列的调用，
     * 最后来到NioServerSocketChannel中，执行真正的doBind方法。
     */
    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    /**
     * @Author: PP-jessica
     * @Description:调用链路从这里跑到了ChannelHandler中
     */
    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        //找到对bind事件感兴趣的handler
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_BIND);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            //这个时候肯定是单线程执行器接管了channel，所以会走这个分支
            next.invokeBind(localAddress, promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeBind(localAddress, promise);
                }
            }, promise, null);
        }
        return promise;
    }

    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                //每次都要调用handler()方法来获得handler，但是接口中的handler方法是在哪里实现的呢？
                //在DefaultChannelHandlerContext类中，这也提醒着我们，我们创建的context节点是DefaultChannelHandlerContext节点。
                ((ChannelOutboundHandler) handler()).bind(this, localAddress, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            bind(localAddress, promise);
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {

        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeConnect(remoteAddress, localAddress, promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeConnect(remoteAddress, localAddress, promise);
                }
            }, promise, null);
        }
        return promise;
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).connect(this, remoteAddress, localAddress, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            connect(remoteAddress, localAddress, promise);
        }
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DISCONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeDisconnect(promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeDisconnect(promise);
                }
            }, promise, null);
        }
        return promise;
    }

    private void invokeDisconnect(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).disconnect(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            disconnect(promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:关闭连接的方法，这个方法会放在最后优雅停机和释放资源的时候讲解
     */
    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CLOSE);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeClose(promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeClose(promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeClose(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).close(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            close(promise);
        }
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            return promise;
        }
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DEREGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeDeregister(promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeDeregister(promise);
                }
            }, promise, null);
        }

        return promise;
    }

    private void invokeDeregister(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).deregister(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            deregister(promise);
        }
    }

    @Override
    public ChannelHandlerContext read() {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_READ);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeRead();
        } else {
            Tasks tasks = next.invokeTasks;
            if (tasks == null) {
                next.invokeTasks = tasks = new Tasks(next);
            }
            executor.execute(tasks.invokeReadTask);
        }

        return this;
    }

    private void invokeRead() {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).read(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            read();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:重构后的write方法的入口。由此可以看见，其实write方法和flush方法是分开执行的
     */
    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    /**
     * @Author: PP-jessica
     * @Description:这个方法就把write和flush分隔开了
     */
    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        //在这里能看见write方法中flush为false，说明只是调用了write方法把数据写入缓冲队列，并没有调用flush方法
        //把要发送的消息flush到socket缓冲区中
        write(msg, false, promise);
        return promise;
    }

    /**
     * @Author: PP-jessica
     * @Description:调用write方法，其实是一个代理，真正的执行方法为invokeWrite0
     */
    private void invokeWrite(Object msg, ChannelPromise promise) {
        if (invokeHandler()) {
            invokeWrite0(msg, promise);
        } else {
            write(msg, promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:真正执行了write方法的方法
     */
    private void invokeWrite0(Object msg, ChannelPromise promise) {
        try {
            //该方法是出站方法，所以会一路传递到head节点中，在头节点中再调用unsafe的write方法
            ((ChannelOutboundHandler) handler()).write(this, msg, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个方法是和write分开的，先调用write方法，再调用flush方法，会走到这个flush方法内，寻找对flush节点
     * 感兴趣的handler，如果调用的是writeAndFlush方法，就不会走这个方法了，而是另外的一套逻辑
     */
    @Override
    public ChannelHandlerContext flush() {
        //寻找flush的handler，如果没有就会跳过链表上的所有节点，直接来到头节点中执行flush方法
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_FLUSH);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeFlush();
        } else {
            //这里会判断一下，如果当前执行方法的线程不是单线程执行器，那就把invokeFlush方法封装到tasks中，就是本类
            //新添加的成员变量，然后把这个tasks中的runnable提交给这个pipeline对应的channel的单线程执行器中
            //让单线程执行器来执行invokeFlush方法
            Tasks tasks = next.invokeTasks;
            if (tasks == null) {
                next.invokeTasks = tasks = new Tasks(next);
            }
            //这里就是把任务提交给单线程执行器，注意哦，这里invokeFlush方法内部执行的只是flush方法。
            //把消息从写缓冲队列中刷新出去而已，在这之前，消息都已经放进写缓冲队列中了
            safeExecute(executor, tasks.invokeFlushTask, channel().newPromise(), null);
        }

        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:调用flush方法
     */
    private void invokeFlush() {
        //判断handler是否可以处理数据
        if (invokeHandler()) {
            //执行flush方法
            invokeFlush0();
        } else {
            //走到这里说明handler还不能处理数据，也就是不能调用flush方法，那就继续寻找下一个可以调用flush的方法
            flush();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:在该方法中可以调用flush来方法了
     */
    private void invokeFlush0() {
        try {
            //这里如果在调用flush方法的过程中出现异常，会执行catch中的方法，回调invokeExceptionCaught方法
            ((ChannelOutboundHandler) handler()).flush(this);
        } catch (Throwable t) {
            //这里就会出发异常方法的回调，实际上就是回调ExceptionCaught方法
            notifyHandlerException(t);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个方法表明write和flush是没有分开的，执行了write后就会执行flush。这两个操作是连接在一起的
     */
    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        //这里会发现write中的flush设置为乐true
        write(msg, true, promise);
        return promise;
    }

    /**
     * @Author: PP-jessica
     * @Description:执行write和flush方法
     */
    private void invokeWriteAndFlush(Object msg, ChannelPromise promise) {
        if (invokeHandler()) {
            //执行write方法，把消息写入到写缓冲队列中
            invokeWrite0(msg, promise);
            //执行flush方法，刷新写缓冲队列，把写缓冲队列中的消息发送到socket缓冲区中
            invokeFlush0();
        } else {
            writeAndFlush(msg, promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:重构之后的write方法
     */
    private void write(Object msg, boolean flush, ChannelPromise promise) {
        ObjectUtil.checkNotNull(msg, "msg");
        try {
            if (isNotValidPromise(promise, true)) {
                ReferenceCountUtil.release(msg);
                return;
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }
        //在这里根据flush的值寻找可以处理write事件的handler或者是寻找可以处理write和flush事件的handler
        final AbstractChannelHandlerContext next = findContextOutbound(flush ?
                (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
        //这里的这个方法实际上会根据内存泄露检测级别，决定是否记录此处的ByteBuf的调用轨迹。
        final Object m = pipeline.touch(msg, next);
        //获取单线程执行器
        EventExecutor executor = next.executor();
        //判断当前线程是否为单线程执行器
        if (executor.inEventLoop(Thread.currentThread())) {
            //如果是单线程执行器，并且flush为true，就直接执行下面这个方法
            if (flush) {
                next.invokeWriteAndFlush(m, promise);
            } else {
                //如果true为false，就执行下面这个方法
                next.invokeWrite(m, promise);
            }
        } else {
            //走到这里说明不是单线程执行器，现在就可以封装一个task了，然后把task提交给单线程执行器去执行即可
            final AbstractWriteTask task;
            if (flush) {
                //根据flush的值封装WriteAndFlushTask或者是WriteTask
                //这里封装的是WriteAndFlushTask
                task = WriteAndFlushTask.newInstance(next, m, promise);
            }  else {
                //这里封装的是WriteTask
                //注意，WriteAndFlushTask和WriteTask类其实都继承了runnable，都是可以被线程执行的异步任务
                task = WriteTask.newInstance(next, m, promise);
            }
            //把上面封装好的异步任务提交到单线程执行器中
            if (!safeExecute(executor, task, promise, m)) {
                //如果提交失败了，就取消这个任务
                task.cancel();
            }
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        //PromiseNotificationUtil.tryFailure(promise, cause, promise instanceof VoidChannelPromise ? null : logger);
    }

    private void notifyHandlerException(Throwable cause) {
        if (inExceptionCaught(cause)) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by a user handler " +
                                "while handling an exceptionCaught event", cause);
            }
            return;
        }
        //异常方法回调了，实际上就是ExceptionCaught这个方法
        invokeExceptionCaught(cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        do {
            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if (t == null) {
                        break;
                    }
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        } while (cause != null);

        return false;
    }


    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }


    @Override
    public ChannelFuture newSucceededFuture() {
        ChannelFuture succeededFuture = this.succeededFuture;
        if (succeededFuture == null) {
            this.succeededFuture = succeededFuture = new DefaultChannelPromise(channel(), executor());
        }
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        //return new FailedChannelFuture(channel(), executor(), cause);
        return null;
    }

    private boolean isNotValidPromise(ChannelPromise promise, boolean allowVoidPromise) {
        if (promise == null) {
            throw new NullPointerException("promise");
        }
        if (promise.isDone()) {
            if (promise.isCancelled()) {
                return true;
            }
            throw new IllegalArgumentException("promise already done: " + promise);
        }
        if (promise.channel() != channel()) {
            throw new IllegalArgumentException(String.format(
                    "promise.channel does not match: %s (expected: %s)", promise.channel(), channel()));
        }
        if (promise.getClass() == DefaultChannelPromise.class) {
            return false;
        }
        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(
                    StringUtil.simpleClassName(AbstractChannel.CloseFuture.class) + " not allowed in a pipeline");
        }
        return false;
    }

    private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            //为什么获取后一个？因为是入站处理器，数据从前往后传输
            ctx = ctx.next;
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            //为什么获取前一个？因为是出站处理器，数据从后往前传输
            ctx = ctx.prev;
            //做&运算，判断事件合集中是否包含该事件
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    /**
     * @Author: PP-jessica
     * @Description:把链表中的ChannelHandler的状态设置为删除完成
     */
    final void setRemoved() {
        handlerState = REMOVE_COMPLETE;
    }

    /**
     * @Author: PP-jessica
     * @Description:把链表中的ChannelHandler的状态设置为添加完成
     */
    final boolean setAddComplete() {
        for (;;) {
            int oldState = handlerState;
            if (oldState == REMOVE_COMPLETE) {
                return false;
            }
            if (HANDLER_STATE_UPDATER.compareAndSet(this, oldState, ADD_COMPLETE)) {
                return true;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:把链表中的ChannelHandler的状态设置为等待添加
     */
    final void setAddPending() {
        boolean updated = HANDLER_STATE_UPDATER.compareAndSet(this, INIT, ADD_PENDING);
        assert updated;
    }

    /**
     * @Author: PP-jessica
     * @Description:在该方法中，ChannelHandler的添加状态将变为添加完成，然后ChannelHandler调用它的
     * handlerAdded方法
     */
    final void callHandlerAdded() throws Exception {
        //在这里改变channelhandler的状态
        if (setAddComplete()) {
            handler().handlerAdded(this);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:回调链表中节点的handlerRemoved方法，该方法在ChannelPipeline中有节点被删除时被调用。
     */
    final void callHandlerRemoved() throws Exception {
        try {
            if (handlerState == ADD_COMPLETE) {
                handler().handlerRemoved(this);
            }
        } finally {
            setRemoved();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:判断ChannelPipeline中节点的状态是否为ADD_COMPLETE，只有状态为ADD_COMPLETE时，handler才可以处理数据
     */
    private boolean invokeHandler() {
        int handlerState = this.handlerState;
        return handlerState == ADD_COMPLETE || (!ordered && handlerState == ADD_PENDING);
    }

    @Override
    public boolean isRemoved() {
        return handlerState == REMOVE_COMPLETE;
    }


    /**
     * @Author: PP-jessica
     * @Description:该方法就可以得到用户存储在channel这个map中的数据，每一个handler都可以得到
     */
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法的作用就是把一个异步任务提交到channel对应的单线程执行器的任务队列中
     */
    private static boolean safeExecute(EventExecutor executor, Runnable runnable, ChannelPromise promise, Object msg) {
        try {
            executor.execute(runnable);
            return true;
        } catch (Throwable cause) {
            try {
                promise.setFailure(cause);
            } finally {
                if (msg != null) {
                    //这里的这个方法也就可以放开注释了
                    ReferenceCountUtil.release(msg);
                }
            }
            return false;
        }
    }

    @Override
    public String toHintString() {
        return '\'' + name + "' will handle the message from this point.";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ChannelHandlerContext.class) + '(' + name + ", " + channel() + ')';
    }

    /**
     * @Author: PP-jessica
     * @Description:这里就把之前注释的内部类放开了，因为发送消息时会用到
     * 这是个抽象父类，其子类会被真正用到
     */
    abstract static class AbstractWriteTask implements Runnable {

        private static final boolean ESTIMATE_TASK_SIZE_ON_SUBMIT =
                SystemPropertyUtil.getBoolean("io.netty.transport.estimateSizeOnSubmit", true);

        private static final int WRITE_TASK_OVERHEAD =
                SystemPropertyUtil.getInt("io.netty.transport.writeTaskSizeOverhead", 48);

        private final Recycler.Handle<AbstractWriteTask> handle;
        private AbstractChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;
        private int size;

        @SuppressWarnings("unchecked")
        private AbstractWriteTask(Recycler.Handle<? extends AbstractWriteTask> handle) {
            this.handle = (Recycler.Handle<AbstractWriteTask>) handle;
        }

        protected static void init(AbstractWriteTask task, AbstractChannelHandlerContext ctx,
                                   Object msg, ChannelPromise promise) {
            task.ctx = ctx;
            task.msg = msg;
            task.promise = promise;

            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                task.size = ctx.pipeline.estimatorHandle().size(msg) + WRITE_TASK_OVERHEAD;
                ctx.pipeline.incrementPendingOutboundBytes(task.size);
            } else {
                task.size = 0;
            }
        }

        @Override
        public final void run() {
            try {
                decrementPendingOutboundBytes();
                write(ctx, msg, promise);
            } finally {
                recycle();
            }
        }

        void cancel() {
            try {
                decrementPendingOutboundBytes();
            } finally {
                recycle();
            }
        }

        private void decrementPendingOutboundBytes() {
            if (ESTIMATE_TASK_SIZE_ON_SUBMIT) {
                ctx.pipeline.decrementPendingOutboundBytes(size);
            }
        }

        private void recycle() {
            // Set to null so the GC can collect them directly
            ctx = null;
            msg = null;
            promise = null;
            handle.recycle(this);
        }

        protected void write(AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ctx.invokeWrite(msg, promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个类用来封装write方法
     */
    static final class WriteTask extends AbstractWriteTask implements SingleThreadEventLoop.NonWakeupRunnable {

        private static final Recycler<WriteTask> RECYCLER = new Recycler<WriteTask>() {
            @Override
            protected WriteTask newObject(Handle<WriteTask> handle) {
                return new WriteTask(handle);
            }
        };
        //这里用到了对象池，为什么要用到对象池呢？
        //因为在Netty构建的服务端和客户端，主要作用就是不停地收发消息，所以，如果发送消息的操作如果是业务线程发起的，是不是就要频繁地
        //封装WriteTask了？所以可以用对象池优化，提高性能
        static WriteTask newInstance(
                AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            WriteTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        private WriteTask(Recycler.Handle<WriteTask> handle) {
            super(handle);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个类是用来封装WriteAndFlushTask的
     */
    static final class WriteAndFlushTask extends AbstractWriteTask {

        private static final Recycler<WriteAndFlushTask> RECYCLER = new Recycler<WriteAndFlushTask>() {
            @Override
            protected WriteAndFlushTask newObject(Handle<WriteAndFlushTask> handle) {
                return new WriteAndFlushTask(handle);
            }
        };

        //用对象池的原因同上
        static WriteAndFlushTask newInstance(
                AbstractChannelHandlerContext ctx, Object msg,  ChannelPromise promise) {
            WriteAndFlushTask task = RECYCLER.get();
            init(task, ctx, msg, promise);
            return task;
        }

        private WriteAndFlushTask(Recycler.Handle<WriteAndFlushTask> handle) {
            super(handle);
        }

        @Override
        public void write(AbstractChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            super.write(ctx, msg, promise);
            ctx.invokeFlush();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个task是给多个方法使用的，封装其为异步任务
     */
    private static final class Tasks {
        private final AbstractChannelHandlerContext next;
        private final Runnable invokeChannelReadCompleteTask = new Runnable() {
            @Override
            public void run() {
                next.invokeChannelReadComplete();
            }
        };
        private final Runnable invokeReadTask = new Runnable() {
            @Override
            public void run() {
                next.invokeRead();
            }
        };
        private final Runnable invokeChannelWritableStateChangedTask = new Runnable() {
            @Override
            public void run() {
                next.invokeChannelWritabilityChanged();
            }
        };
        //在这里，封装为runnable了，然后执行了invokeFlush方法
        private final Runnable invokeFlushTask = new Runnable() {
            @Override
            public void run() {
                next.invokeFlush();
            }
        };

        Tasks(AbstractChannelHandlerContext next) {
            this.next = next;
        }
    }
}

