package com.pp.netty.channel;


import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.util.DefaultAttributeMap;
import com.pp.netty.util.ReferenceCountUtil;
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

    //判断Channel是否可写
    @Override
    public boolean isWritable() {
        ChannelOutboundBuffer buf = unsafe.outboundBuffer();
        return buf != null && buf.isWritable();
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
        return null;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return null;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return null;
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
         * 还有，这个写缓冲区内部实际上使用Entry链表来存储待刷新的消息的
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
         * @Description:暂时不做实现，接下来的一些方法都不做实现，等之后讲到了再实现
         */
        @Override
        public final void disconnect(final ChannelPromise promise) {}

        @Override
        public final void close(final ChannelPromise promise) {}

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

        @Override
        public final void deregister(final ChannelPromise promise) {}

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
                //没有引入这个类型，只引入了接口，没有引入实现类
                //注意哦，ByteBuf都会被包装成可以检测是否内存泄漏的ByteBuf的类型，也就是AdvancedLeakAwareByteBuf类型
                //这个类型的父类WrappedByteBuf中会持有原ByteBuf的引用，所以判断ByteBuf是否为直接内存的时候，
                //会先从父类中得到直接内存的引用，在判断是否为直接内存，逻辑就在下面的方法内。下面的方法在AbstractNioByteChannel类中
                msg = filterOutboundMessage(msg);
                System.out.println(msg);
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
            //消息发送到socket的缓冲区中，才会被真正发送出去
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
                        //走到这里说明channel是不活跃但是打开的状态，可能就是连接断开了，所以这里设置刷新失败，并且触发异常
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
                //下面暂且先注释掉一些方法，后面讲到关闭channel时会详解讲解
                if (t instanceof IOException && config().isAutoClose()) {
                    initialCloseCause = t;
                    //close(voidPromise(), t, newClosedChannelException(t), false);
                } else {
                    try {
                        //shutdownOutput(voidPromise(), t);
                    } catch (Throwable t2) {
                        initialCloseCause = t;
                        //close(voidPromise(), t2, newClosedChannelException(t), false);
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

    protected abstract void doBeginRead() throws Exception;

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
