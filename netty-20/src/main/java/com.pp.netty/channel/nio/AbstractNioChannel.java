package com.pp.netty.channel.nio;

import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.buffer.ByteBufUtil;
import com.pp.netty.channel.*;
import com.pp.netty.channel.Channel;
import com.pp.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractNioChannel extends AbstractChannel {

    //该抽象类是serversocketchannel和socketchannel的公共父类
    private final SelectableChannel ch;

    //channel要关注的事件
    protected final int readInterestOp;

    //channel注册到selector后返回的key
    volatile SelectionKey selectionKey;
    //是否还有未读取的数据
    boolean readPending;

    private final Runnable clearReadPendingRunnable = new Runnable() {
        @Override
        public void run() {
            clearReadPending0();
        }
    };

    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private SocketAddress requestedRemoteAddress;


    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            //设置服务端channel为非阻塞模式
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                //有异常直接关闭channel
                ch.close();
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
            throw new RuntimeException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }


    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }


    protected final void clearReadPending() {
        if (isRegistered()) {
            EventLoop eventLoop = eventLoop();
            if (eventLoop.inEventLoop(Thread.currentThread())) {
                clearReadPending0();
            } else {
                eventLoop.execute(clearReadPendingRunnable);
            }
        } else {
            readPending = false;
        }
    }

    private void clearReadPending0() {
        readPending = false;
        ((AbstractNioUnsafe) unsafe()).removeReadOp();
    }


    public interface NioUnsafe extends Unsafe {

        SelectableChannel ch();

        void finishConnect();

        void read();

        void forceFlush();
    }

    /**
     * @Author: PP-jessica
     * @Description:终于又引入了一个unsafe的抽象内部类
     */
    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {


        /**
         * @Author: PP-jessica
         * @Description:从感兴趣的集合中删除读事件
         */
        protected final void removeReadOp() {
            SelectionKey key = selectionKey();
            if (!key.isValid()) {
                return;
            }
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法回到了原本的位置
         */
        @Override
        public final void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            //查看通道是否打开
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }
            try {
                //该值不为空，说明已经有连接存在了，不能再次连接
                if (connectPromise != null) {
                    throw new ConnectionPendingException();
                }
                //现在还不是活跃状态
                boolean wasActive = isActive();
                //这里会返回false
                if (doConnect(remoteAddress, localAddress)) {
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    //可以为本次连接设置定时任务，检查是否连接超时了
                    connectPromise = promise;
                    requestedRemoteAddress = remoteAddress;
                    //这个getConnectTimeoutMillis是用户在客户端启动时配置的参数，如果没有配置，肯定会有一个默认的
                    //默认的超时时间是30s
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        //创建一个超时任务，如果在限定时间内没被取消，就去执行该任务，说明连接超时了，然后关闭channel
                        //在finishConnect()和doClose()中，该任务会被取消。就是连接完成或者通道关闭了了，不需要再去检测了。
                        connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                                ChannelPromise connectPromise = AbstractNioChannel.this.connectPromise;
                                ConnectException cause =
                                        new ConnectException("connection timed out: " + remoteAddress);
                                if (connectPromise != null && connectPromise.tryFailure(cause)) {
                                   //走到这里意味着连接超时，通道就会关闭
                                    close(voidPromise());
                                }
                            }
                        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                    }
                    promise.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            //监听器，判断该future是否被取消了，如果被取消了，那就取消该定时任务，然后关闭channel
                            if (future.isCancelled()) {
                                if (connectTimeoutFuture != null) {
                                    connectTimeoutFuture.cancel(false);
                                }
                                connectPromise = null;
                                close(voidPromise());
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                promise.tryFailure(t);
                closeIfClosed();
            }
        }


        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                return;
            }
            boolean active = isActive();
            boolean promiseSet = promise.trySuccess();
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }
            if (!promiseSet) {
                close(voidPromise());
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法和源码一致了
         */
        @Override
        public final void finishConnect() {
            assert eventLoop().inEventLoop(Thread.currentThread());
            try {
                boolean wasActive = isActive();
                doFinishConnect();
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                //fulfillConnectPromise(connectPromise, annotateConnectException(t, requestedRemoteAddress));
            } finally {
                //检查是否为null，如果不等于null，则说明创建定时任务了，这时候已经连接完成，只要取消该任务就行
                if (connectTimeoutFuture != null) {
                    connectTimeoutFuture.cancel(false);
                }
                connectPromise = null;
            }
        }


        /**
         * @Author: PP-jessica
         * @Description:这个方法会在封装的flushTask中被调用，也就意味着这个方法是在socket可写的情况下被调用的
         * 和注册的write事件是冲突的。所以下面才会判断，如果注册了write事件，就不会调用 super.flush0()
         * 没有注册write事件，才会执行这个异步任务，刷新数据到socket缓冲区中
         */
        @Override
        protected final void flush0() {
            //判断有没有注册write事件，没有注册才会继续向下执行
            if (!isFlushPending()) {
                super.flush0();
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:强制刷新消息的方法，当selector接收到write事件时，就会调用这个方法，强制把写缓冲区的消息刷新到socket中
         * 这里其实还是会调用父类的flush0方法
         */
        @Override
        public final void forceFlush() {
            super.flush0();
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法用来判断是否有数据待刷新，就是是否注册了write事件
         */
        private boolean isFlushPending() {
            SelectionKey selectionKey = selectionKey();
            return selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
        }
    }


    @Override
    protected void doRegister() throws Exception {
        //在这里把channel注册到单线程执行器中的selector上,注意这里的第三个参数this，这意味着channel注册的时候把本身，也就是nio类的channel
        //当作附件放到key上了，之后会用到这个。
        selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
    }


    /**
     * @Author: PP-jessica
     * @Description:取消多路复用器上对应的channel的selectionKey
     */
    @Override
    protected void doDeregister() throws Exception {
        eventLoop().cancel(selectionKey());
    }

    @Override
    protected void doBeginRead() throws Exception {
        final SelectionKey selectionKey = this.selectionKey;
        //检查key是否是有效的
        if (!selectionKey.isValid()) {
            return;
        }
        //还没有设置感兴趣的事件，所以得到的值为0
        final int interestOps = selectionKey.interestOps();
        //interestOps中并不包含readInterestOp
        if ((interestOps & readInterestOp) == 0) {
            //设置channel关注的事件，读事件
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }


    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(buf);
            //return Unpooled.EMPTY_BUFFER;
            return null;
        }
        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            ByteBuf directBuf = alloc.directBuffer(readableBytes);
            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
            ReferenceCountUtil.safeRelease(buf);
            return directBuf;
        }
//        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
//        if (directBuf != null) {
//            directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
//            ReferenceCountUtil.safeRelease(buf);
//            return directBuf;
//        }
        return buf;
    }

    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    protected abstract void doFinishConnect() throws Exception;

    @Override
    protected void doClose() throws Exception {}

}
