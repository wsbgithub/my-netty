package com.pp.netty.channel;


import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.channel.socket.ChannelOutputShutdownEvent;
import com.pp.netty.channel.socket.ChannelOutputShutdownException;
import com.pp.netty.util.DefaultAttributeMap;
import com.pp.netty.util.ReferenceCountUtil;
import com.pp.netty.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @Author: PP-jessica
 * @Description:channel接口的抽象实现类，这里面有许多重要方法没有实现，有很多方法没有引进，接下来的几节课会依次引入
 * 该类中的bind，close等等方法，都是定义好的模版方法，在子类中有真正的被调用的实现方法，以doxxxx开头。
 */
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel{

    private static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    /**
     * @Author: PP-jessica
     * @Description:当创建的是客户端channel时，parent为serversocketchannel
     * 如果创建的为服务端channel，parent则为null
     */
    private final Channel parent;

    private final ChannelId id;
    /**
     * @Author: PP-jessica
     * @Description:加入unsafe属性了
     */
    private final Unsafe unsafe;

    /**
     * @Author: PP-jessica
     * @Description:添加DefaultChannelPipeline属性
     */
    private final DefaultChannelPipeline pipeline;

    private final VoidChannelPromise unsafeVoidPromise = new VoidChannelPromise(this, false);

    /**
     * @Author: PP-jessica
     * @Description:看名字也可以猜出，这个future是在channel关闭的时候使用的，是一个静态内部类
     */
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;

    private volatile SocketAddress remoteAddress;

    //这个成员变量代表channel是否正在关闭了
    private boolean closeInitiated;
    private Throwable initialCloseCause;

    /**
     * @Author: PP-jessica
     * @Description:每一个channel都要绑定到一个eventloop上
     */
    private volatile EventLoop eventLoop;

    /**
     * @Author: PP-jessica
     * @Description:该channel是否注册过
     */
    private volatile boolean registered;

    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }


    protected AbstractChannel(Channel parent, ChannelId id) {
        this.parent = parent;
        this.id = id;
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    @Override
    public final ChannelId id() {
        return id;
    }

    protected ChannelId newId() {
        return DefaultChannelId.newInstance();
    }

    protected DefaultChannelPipeline newChannelPipeline() {
        //把创建出的channel传入DefaultChannelPipeline；
        return new DefaultChannelPipeline(this);
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public Channel parent() {
        return parent;
    }


    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }


    public ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    /**
     * @Author: PP-jessica
     * @Description:得到本地地址
     */
    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                return null;
            }
        }
        return localAddress;
    }

    /**
     * @Author: PP-jessica
     * @Description:得到远程地址
     */
    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    protected abstract boolean isCompatible(EventLoop loop);

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public Channel flush() {
        pipeline.flush();
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:对比上节课，该方法有所变动
     */
    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(promise);
    }

    @Override
    public Channel read() {
        pipeline.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline.newPromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return pipeline.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline.newFailedFuture(cause);
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    protected abstract AbstractUnsafe newUnsafe();

    /**
     * @Author: PP-jessica
     * @Description:Unsafe的抽象内部类
     */
    protected abstract class AbstractUnsafe implements Unsafe {

        /**
         * @Author: PP-jessica
         * @Description:写缓冲区终于引入进来了，写缓冲区的功能就是保存channel要发送的数据。为什么不直接发送，而要先保存再发送呢？
         * 因为在netty中，单线程执行器要做很多事，不仅要执行各种事件的方法，比如write，flush，bind，read，register等等，还要执行
         * 用户提交的各种异步任务，定时任务等等。所以这里使用了一个缓冲区，如果要发送消息，可以先调用write方法，把消息放到写缓冲区中
         * 然后在合适的时机再调用flush，把消息发送到socket中。
         * 但是这里也要注意，每一个channel其实都会对应一个写缓冲区，所以这就决定了写缓冲区的高写水位线不可能太高，如果太高的话，并发情况下
         * 每个channel都要向写缓冲区中存放太多msg，会占用很多内存的
         * 注意哦，这个写缓冲区和socket中的缓冲区并不是一回事，这个要弄清楚
         * 还有，这个写缓冲区内部实际上使用Entry链表来存储带刷新的消息的
         */
        private volatile ChannelOutboundBuffer outboundBuffer = new ChannelOutboundBuffer(AbstractChannel.this);
        //在这里终于出现了动态字节分配器
        private RecvByteBufAllocator.Handle recvHandle;
        private boolean neverRegistered = true;
        private boolean inFlush0;

        private void assertEventLoop() {
            assert !registered || eventLoop.inEventLoop(Thread.currentThread());
        }


        /**
         * @Author: PP-jessica
         * @Description:该方法返回动态内存分配的处理器
         */
        @Override
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config().getRecvByteBufAllocator().newHandle();
            }
            return recvHandle;
        }

        /**
         * @Author: PP-jessica
         * @Description:返回写缓冲区
         */
        @Override
        public final ChannelOutboundBuffer outboundBuffer() {
            return outboundBuffer;
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法终于回到了它本来的位置
         */
        @Override
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            if (eventLoop == null) {
                throw new NullPointerException("eventLoop");
            }
            //检查channel是否注册过，注册过就手动设置promise失败
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }
            //判断当前使用的执行器是否为NioEventLoop，如果不是手动设置失败
            if (!isCompatible(eventLoop)) {
                promise.setFailure(new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }
            //稍微学过netty的人都知道，一个channel绑定一个单线程执行器。终于在这里，我们看到channel绑定了单线程执行器
            //接着channel，不管是客户端还是服务端的，会把自己注册到绑定的单线程执行器中的selector上
            AbstractChannel.this.eventLoop = eventLoop;
            //又看到这个方法了，又一次说明在netty中，channel注册，绑定，连接等等都是异步的，由单线程执行器来执行
            if (eventLoop.inEventLoop(Thread.currentThread())) {
                register0(promise);
            } else {
                try {
                    //如果调用该放的线程不是netty的线程，就封装成任务由线程执行器来执行
                    eventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            register0(promise);
                        }
                    });
                } catch (Throwable t) {
                    //该方法先不做实现，等引入unsafe之后会实现
                    //closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
        }

        private void register0(ChannelPromise promise) {
            try {
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                boolean firstRegistration = neverRegistered;
                //真正的注册方法
                doRegister();
                //修改注册状态
                neverRegistered = false;
                registered = true;
                //回调链表中的方法，链表中的每一个节点都会执行它的run方法，在run方法中
                //ChannelPipeline中每一个节点中handler的handlerAdded方法，在执行callHandlerAdded的时候，handler的添加状态
                //更新为ADD_COMPLETE
                pipeline.invokeHandlerAddedIfNeeded();
                //把成功状态赋值给promise，这样它可以通知回调函数执行
                //我们在之前注册时候，把bind也放在了回调函数中
                safeSetSuccess(promise);
                //channel注册成功后回调每一个handler的channelRegister方法
                pipeline.fireChannelRegistered();
                //这里通道还不是激活状态，因为还未绑定端口号，所以下面的分支并不会进入
                //等绑定了端口号之后，还会执行一次 pipeline.fireChannelActive方法，
                //这时候每一个handler中的ChannelActive方法将会被回调
                if (isActive()) {
                    if (firstRegistration) {
                        //触发channelActive回调
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        //在这里有可能无法关注读事件
                        beginRead();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            //这里一定为false，因为channel还未绑定端口号，肯定不是激活状态
            boolean wasActive = isActive();
            try {
                doBind(localAddress);
            } catch (Exception e) {
                safeSetFailure(promise, e);
            }
            //这时候一定为true了
            if (!wasActive && isActive()) {
                //然后会向单线程执行器中提交任务，任务重会执行ChannelPipeline中每一个节点中handler的ChannelActive方法
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireChannelActive();
                    }
                });
            }

            safeSetSuccess(promise);
        }

        /**
         * @Author: PP-jessica
         * @Description:这时候就可以实现了
         */
        @Override
        public final void disconnect(final ChannelPromise promise) {}

        /**
         * @Author: PP-jessica
         * @Description:该方法是正常关闭channel的入口方法
         * 客户端可以调用该方法，而当服务端接收到客户端关闭channel的通知后，也就是接收到-1了，
         * 也会调用该方法，关闭服务端管理的客户端的channel
         */
        @Override
        public final void close(final ChannelPromise promise) {
            assertEventLoop();
            //这里创建的异常是要传递给写缓冲区的，因为channel要关闭了，写缓冲区不能再存放消息对象了
            ClosedChannelException closedChannelException = new ClosedChannelException();
            close(promise, closedChannelException, closedChannelException, false);
        }

        /**
         * @Author: PP-jessica
         * @Description:这里也是半关闭方法的过渡
         */
        @UnstableApi
        public final void shutdownOutput(final ChannelPromise promise) {
            assertEventLoop();
            shutdownOutput(promise, null);
        }

        /**
         * @Author: PP-jessica
         * @Description:这里就是真正开始处理channel半关闭操作的方法
         */
        private void shutdownOutput(final ChannelPromise promise, Throwable cause) {
            //前面都是一些判断，这个promise是从之前的外层方法创建来的，这意味着其实调用半关闭方法也是可以添加监听器的
            if (!promise.setUncancellable()) {
                return;
            }
            //这里得到写缓冲区，还记得写缓冲区吧。然后判断写缓冲区的状态
            //写缓冲区在channel关闭的时候会被置为null，这一点大家可以看看前面的课程回顾一下
            //所以这里会判断写缓冲区是否为null，如果为null，意味着channel已经关闭了，通过close方法
            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                //设置失败，然后直接返回
                promise.setFailure(new ClosedChannelException());
                return;
            }
            //这里就把写缓冲区设置为null了，原因上面解释过了
            //半关闭状态，可以接收消息，但不能再向写缓冲区存放消息了
            this.outboundBuffer = null;
            //创建一个异常，这个是要在写缓冲区中使用的，会传递到写缓冲区
            final Throwable shutdownCause = cause == null ?
                    new ChannelOutputShutdownException("Channel output shutdown") :
                    new ChannelOutputShutdownException("Channel output shutdown", cause);
            //这个方法很重要，一定要点进去看逻辑
            //返回一个执行器，也可能返回null
            Executor closeExecutor = prepareToClose();
            if (closeExecutor != null) {
                //注意，这里使用的是全局执行器了，如果不为null的话，就使用这个全局执行器
                //为什么使用这个执行器在prepareToClose方法中解释了
                //当然，这里其实是个bug，很明显，prepareToClose()在半关闭方法中并不太适用，但大家明白这个道理就行
                closeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //在这里，真正关闭了channel
                            //在NioSocketChannel中调用了javaChannel().shutdownOutput()这行代码
                            //可以点进去看一下
                            //这里是由closeExecutor执行的半关闭方法
                            doShutdownOutput();
                            //设置操作成功
                            promise.setSuccess();
                        } catch (Throwable err) {
                            //有异常则设置操作失败
                            promise.setFailure(err);
                        } finally {
                            //提交一个异步任务，清除写缓冲区中的消息，然后回调ChannelOutputShutdownEvent事件
                            //那么就可以在这个方法中定义一些行为，这就是用户自己的需求了
                            //这时候肯定有些朋友对半关闭和close感到迷糊，不清楚这两个关闭究竟有什么区别
                            //简单来说，区别就是close关闭channel最终会释放文件描述符，这个和底层的tcp有关了
                            //如果是半关闭，则不会对文件描述符进行操作，而是直接关闭socket缓冲区，关闭对应的初入或输出流
                            //这就意味着接下来，要向处理文件描述符，还需要接着调用close方法，这是针对于服务端来说的
                            eventLoop().execute(new Runnable() {
                                @Override
                                public void run() {
                                    closeOutboundBufferForShutdown(pipeline, outboundBuffer, shutdownCause);
                                }
                            });
                        }
                    }
                });
            } else {
                try {
                    //走到这里说明closeExecutor为null，仍由单线程执行器执行半关闭方法即可
                    doShutdownOutput();
                    //设置成功
                    promise.setSuccess();
                } catch (Throwable err) {
                    promise.setFailure(err);
                } finally {
                    //清除写缓冲区中的消息，然后回调ChannelOutputShutdownEvent事件
                    //注意，这里传入的outboundBuffer是
                    //final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer中的outboundBuffer
                    //并不是置为null的成员变量outboundBuffer
                    closeOutboundBufferForShutdown(pipeline, outboundBuffer, shutdownCause);
                }
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:清除写缓冲区的数据，然后回调ChannelOutputShutdownEvent事件
         */
        private void closeOutboundBufferForShutdown(
                ChannelPipeline pipeline, ChannelOutboundBuffer buffer, Throwable cause) {
            //设置发送失败
            buffer.failFlushed(cause, false);
            //清理写缓冲区中的Entry对象
            buffer.close(cause, true);
            //回调事件，到此为止，半关闭的操作其实就进行完了
            pipeline.fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE);
        }


        /**
         * @Author: PP-jessica
         * @Description:关闭channel的方法
         */
        private void close(final ChannelPromise promise, final Throwable cause,
                           final ClosedChannelException closeCause, final boolean notify) {
            if (!promise.setUncancellable()) {
                //close的操作取消直接返回即可
                return;
            }
            //closeInitiated初始值默认为0，也就是false，所以走到这里的时候
            //肯定是false，因为还未进入channel关闭状态
            //这里我们分析的是客户端主动调用该方法的时候
            //如果程序在运行中出现错误，也会迫不得已关闭channel，这时候很有可能已经走过这个方法了，然后用户又
            //调用了关闭channel的方法，这时候closeInitiated很可能就为true了
            //所以这里也能看得出来，这个变量实际上还会起到关闭channel只执行一次的作用
            if (closeInitiated) {
                //如果走到这里，说明已经进入关闭状态了
                //这里会判断closeFuture是否已完成。如果已经完成，就设置promise状态成功
                //这里大家把closeFuture想象成一个独立的个体，在某些情况下它确实会被当作参数传进方法中
                //但是在这里，它是拿来即用的，是一个独立的成员变量当关闭channel成功后，直接给这个成员变量设置状态即可，在其他地方判断这个成员变量
                //的状态就行
                if (closeFuture.isDone()) {
                    safeSetSuccess(promise);
                } else if (!(promise instanceof VoidChannelPromise)) {
                    //走到这里就意味着channel正在关闭，但还没有关闭成功呢
                    //这里有个小判断，就是判断如果传进来的promise是不是VoidChannelPromise类型的
                    //因为当服务端接收到-1时，表明服务端管理的客户端channel要关闭
                    //这时候服务端会发起一个关闭channel的动作，也会传递一个promise，这个promise必须为VoidChannelPromise类型的
                    //表示没有返回值的promise
                    //走到这里就意味着不是VoidChannelPromise类型的，那就不是服务端发起的关闭，所以给这个成员变量增加一个监听器
                    //当关闭channel成功了，将promise设置为成功状态
                    closeFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            promise.setSuccess();
                        }
                    });
                }
                //走到这里就意味着上面两个分支都没有走，说明没有关闭成功呢，但是promise属于VoidChannelPromise类型
                //那就直接退出即可，反正正在关闭channel
                return;
            }
            //走到这里说明上面的那个if没有走，这说明channel刚才没处于正在关闭的状态，现在才要关闭
            //所以更改状态
            closeInitiated = true;
            //还没有关闭，所以channel的状态肯定返回true
            final boolean wasActive = isActive();
            //再次得到写缓冲区
            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            //缓冲区设为null
            this.outboundBuffer = null;
            //这个方法之前分析过了，这里就不再分析了
            Executor closeExecutor = prepareToClose();
            if (closeExecutor != null) {
                closeExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                           //真正关闭channel的方法
                            doClose0(promise);
                        } finally {
                            //这里会把任务提交给单线程执行器
                            invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    //注意，这里的这个异步任务是在全局执行器中被提交的，如果执行到这里，就意味着全局执行器
                                    //完成了close的阻塞，当然，前提是用户配置了特定参数
                                    //所以走到这里channel应该已经关闭完成了
                                    //接下来仍然是清扫写缓冲区
                                    if (outboundBuffer != null) {
                                        outboundBuffer.failFlushed(cause, notify);
                                        outboundBuffer.close(closeCause);
                                    }
                                    //回调ChannelInactiveAndDeregister两个方法，wasActive是之前得到值，所以为true
                                    fireChannelInactiveAndDeregister(wasActive);
                                }
                            });
                        }
                    }
                });
            } else {
                try {
                    //走到这里就意味着close的动作是单线程执行器来执行
                    //并且不会在close上阻塞
                    doClose0(promise);
                } finally {//这里并不会为null，局部变量的引用还持有者堆中的对象呢，当方法执行完毕后，全局引用已经为null了
                    //局部引用也没了，写缓冲区对象就会被垃圾回收了
                    if (outboundBuffer != null) {
                        outboundBuffer.failFlushed(cause, notify);
                        outboundBuffer.close(closeCause);
                    }
                }
                //这里要结合flush方法来理解，如果这时候还在执行flush方法，就延迟回调ChannelInactiveAndDeregister方法
                //就是下面这一大段flush方法中的代码，这里会判断channel的状态，然后设置写缓冲区不可写
                ////在把正在刷新消息设置为true
                //            inFlush0 = true;
                //            //首先判断channel是否为活跃的状态
                //            //注意，这里返回的是false，然后取反，才能向分支中继续运行
                //            if (!isActive()) {
                //                try {
                //                    //然后判断channel是否为打开的状态
                //                    if (isOpen()) {
                //                        //走到这里说明channel是不活跃但是打开的状态，可能就是连接断开了，所以这里设置刷新失败，并且处罚异常
                //                        //failFlushed该方法会将代刷新的消息从写缓冲区中删除，同时Entry对象也会被释放到对象池中
                //                        outboundBuffer.failFlushed(new NotYetConnectedException(), true);
                //                    } else {
                //                        //走到这里则说明channel并不活跃，而且不是打开状态，意味着channel已经关闭了，这时候就设置刷新失败，但是不处罚异常
                //                        outboundBuffer.failFlushed(newClosedChannelException(initialCloseCause), false);
                //                    }
                //                } finally {
                //                    inFlush0 = false;
                //                }
                //说实话，现在再看这个判断，我实在想不通为什么此时还会有正在刷新的操作
                //我个人仅仅把这个当做一个兜底方法来看待
                if (inFlush0) {
                    invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            fireChannelInactiveAndDeregister(wasActive);
                        }
                    });
                } else {
                    fireChannelInactiveAndDeregister(wasActive);
                }
            }
        }
        /**
         * @Author: PP-jessica
         * @Description:真正关闭channel的方法
         */
        private void doClose0(ChannelPromise promise) {
            try {
                //关闭channel
                doClose();
                //关闭成功，在这里把closeFuture设置为已关闭状态
                closeFuture.setClosed();
                //设置promise成功
                safeSetSuccess(promise);
            } catch (Throwable t) {
                closeFuture.setClosed();
                //有异常则设置失败
                safeSetFailure(promise, t);
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:取消注册事件的方法
         */
        private void fireChannelInactiveAndDeregister(final boolean wasActive) {
            deregister(voidPromise(), wasActive && !isActive());
        }

        /**
         * @Author: PP-jessica
         * @Description:强制关闭channel
         */
        @Override
        public final void closeForcibly() {
            assertEventLoop();
            try {
                doClose();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:取消注册事件的方法
         */
        private void deregister(final ChannelPromise promise, final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (!registered) {
                safeSetSuccess(promise);
                return;
            }
            //提交一个异步任务，在该任务中取消channel事件
            invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        //取消channel的事件注册
                        doDeregister();
                    } catch (Throwable t) {
                        logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                    } finally {
                        if (fireChannelInactive) {
                            //回调ChannelInactive事件
                            pipeline.fireChannelInactive();
                        }
                        if (registered) {
                            //channel没注册，就回调ChannelUnregistered方法
                            registered = false;
                            pipeline.fireChannelUnregistered();
                        }
                        //设置promise成功
                        safeSetSuccess(promise);
                    }
                }
            });
        }

        @Override
        public final void deregister(final ChannelPromise promise) {
            assertEventLoop();
            deregister(promise, false);
        }

        @Override
        public final void beginRead() {
            assertEventLoop();
            //如果是服务端的channel，这里仍然可能为false
            //那么真正注册读事件的时机，就成了绑定端口号成功之后
            if (!isActive()) {
                return;
            }
            try {
                doBeginRead();
            } catch (final Exception e) {
                //如果出现了异常，就提交异步任务，在任务中抓住异常
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireExceptionCaught(e);
                    }
                });
                close(closeFuture);
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:重构之后的write方法，注意哦，这里的write方法和下面的flush方法，以及上面的一些方法
         * 其实都是经过了管道和unsafe类，才调用到channel中的。这个一定要理清楚
         */
        @Override
        public final void write(Object msg, ChannelPromise promise) {
            //仍然是判断是否为单线程执行器
            assertEventLoop();
            //得到写缓冲区
            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            //如果写缓冲区为null，就设置发送失败的结果
            //注意，write方法也是个异步方法，可以设置promise，有promise就可以设置监听器
            //所以可以用promise设置发送成功或失败
            if (outboundBuffer == null) {
                safeSetFailure(promise, newClosedChannelException(initialCloseCause));
                //发送失败就释放msg，这个msg实际上就是ByteBuf，也就是释放ByteBuf
                ReferenceCountUtil.release(msg);
                return;
            }
            int size;
            try {
                //这里会过滤一下msg的类型，因为发送消息只会发送DirectBuffer或者fileRegion包装的msg，但是我们把fileRegion注释掉了
                //没有引入这个类型
                //注意哦，ByteBuf都会被包装成可以检测是否内存泄漏的ByteBuf的类型，也就是AdvancedLeakAwareByteBuf类型
                //这个类型的父类WrappedByteBuf中会持有原ByteBuf的引用，所以判断ByteBuf是否为直接内存的时候，
                //会先从父类中得到直接内存的引用，在判断是否为直接内存，逻辑就在下面的方法内。下面的方法在AbstractNioByteChannel类中
                msg = filterOutboundMessage(msg);
                //计算要发送的消息的大小，其实就是得到包装msg的ByteBuf的可读字节的大小
                size = pipeline.estimatorHandle().size(msg);
                if (size < 0) {
                    size = 0;
                }
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                ReferenceCountUtil.release(msg);
                return;
            }
            //在这里，就将要发送的消息加入到写缓冲区中了，该消息会被Entry来包装
            //注意哦，这里只是把消息放到写缓冲区中了，并没有真正发送到socket中
            //消息发送大socket的缓冲区中，才会被真正发送出去
            //到这里，write方法实际上就执行完了。下面虽然还有一个doWrite方法，但那个方法是将写缓冲区中的消息发送到socket缓冲区
            //中的，其实是属于flush作用的方法，要在flush方法中被调用
            outboundBuffer.addMessage(msg, size, promise);
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法的作用就是把写缓冲区中的消息刷新到socket中
         * 不过该方法其实是个代理，是最外层的方法，真正执行的其实是doWrite方法
         */
        @Override
        public final void flush() {
            assertEventLoop();
            //得到写缓冲区
            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            //判断其是否为null，这里之所以会有这个判断，是因为在源码的close方法中，关闭channel会把写缓冲区置为null
            //所以如果判断为null，则说明channel已经关闭了
            //现在还没有引入close方法，等最后一节课会将该方法引入
            if (outboundBuffer == null) {
                return;
            }
            //这里的操作就是把写缓冲区中的flushedEntry指向unflushedEntry，unflushedEntry其实就是写缓冲区中
            //第一个要发送的消息数据
            outboundBuffer.addFlush();
            //这个方法就会把消息刷新到socket中
            flush0();
        }


        /**
         * @Author: PP-jessica
         * @Description:把写缓冲区中的消息刷新到socket中
         * 在这个方法中，真正执行这个操作的实际上是doWrite方法，而该方法在NioSocketChannel中被重写了
         */
        @SuppressWarnings("deprecation")
        protected void flush0() {
            //判断是否正在进行刷新操作
            if (inFlush0) {
                return;
            }
            //得到写缓冲区
            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            //判断写缓冲区是否为null，为null说明channel已经被关闭了，同时也要判断写缓冲区中是否有要被刷新的消息
            if (outboundBuffer == null || outboundBuffer.isEmpty()) {
                //上面两个有一个返回true，就直接return
                return;
            }
            //在把正在刷新消息设置为true
            inFlush0 = true;
            //首先判断channel是否为活跃的状态
            //注意，这里返回的是false，然后取反，才能向分支中继续运行
            if (!isActive()) {
                try {
                    //然后判断channel是否为打开的状态
                    if (isOpen()) {
                        //走到这里说明channel是不活跃但是打开的状态，可能就是连接断开了，所以这里设置刷新失败，并且处罚异常
                        //failFlushed该方法会将代刷新的消息从写缓冲区中删除，同时Entry对象也会被释放到对象池中
                        outboundBuffer.failFlushed(new NotYetConnectedException(), true);
                    } else {
                        //走到这里则说明channel并不活跃，而且不是打开状态，意味着channel已经关闭了，这时候就设置刷新失败，但是不处罚异常
                        outboundBuffer.failFlushed(newClosedChannelException(initialCloseCause), false);
                    }
                } finally {
                    inFlush0 = false;
                }
                return;
            }
            try {
                //真正刷新数据到socket中的方法
                doWrite(outboundBuffer);
            } catch (Throwable t) {
                if (t instanceof IOException && config().isAutoClose()) {
                    initialCloseCause = t;
                    close(voidPromise(), t, newClosedChannelException(t), false);
                } else {
                    try {
                        shutdownOutput(voidPromise(), t);
                    } catch (Throwable t2) {
                        initialCloseCause = t;
                        close(voidPromise(), t2, newClosedChannelException(t), false);
                    }
                }
            } finally {
                //刷新完毕，要把该属性重置为false
                inFlush0 = false;
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:确保channel是打开的状态
         */
        protected final boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }
            safeSetFailure(promise, newClosedChannelException(initialCloseCause));
            return false;
        }

        protected final void safeSetSuccess(ChannelPromise promise) {
            if (!promise.trySuccess()) {
                System.out.println("Failed to mark a promise as success because it is done already: "+promise);
            }
        }

        protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!promise.tryFailure(cause)) {
                throw new RuntimeException(cause);
            }
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidPromise());
        }

        private void invokeLater(Runnable task) {
            try {
                eventLoop().execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        @Override
        public final ChannelPromise voidPromise() {
            assertEventLoop();

            return unsafeVoidPromise;
        }

        protected Executor prepareToClose() {
            return null;
        }

    }

    /**
     * @Author: PP-jessica
     * @Description:该方法也被重构了，参数终于正确了
     */
    protected abstract void doWrite(ChannelOutboundBuffer in) throws Exception;

    protected abstract SocketAddress localAddress0();

    protected abstract SocketAddress remoteAddress0();

    protected void doRegister() throws Exception {}

    protected abstract void doBind(SocketAddress localAddress) throws Exception;

    protected void doDeregister() throws Exception {
    }

    protected abstract void doBeginRead() throws Exception;

    @UnstableApi
    protected void doShutdownOutput() throws Exception {
        doClose();
    }

    protected abstract void doClose() throws Exception;

    protected Object filterOutboundMessage(Object msg) throws Exception {
        return msg;
    }

    private ClosedChannelException newClosedChannelException(Throwable cause) {
        ClosedChannelException exception = new ClosedChannelException();
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }


    static final class CloseFuture extends DefaultChannelPromise {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }
    }
}
