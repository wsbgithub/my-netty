package com.pp.netty.channel.nio;

import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.buffer.ByteBufAllocator;
import com.pp.netty.channel.*;
import com.pp.netty.channel.socket.SocketChannelConfig;
import com.pp.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import static com.pp.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;

public abstract class AbstractNioByteChannel extends AbstractNioChannel{

    //就是在这里把每次接收数据的最大次数设定为16了
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
                    StringUtil.simpleClassName(FileRegion.class) + ')';

    //提交给单线程执行器一个异步任务，用于刷新缓冲区
    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            ((AbstractNioUnsafe) unsafe()).flush0();
        }
    };

    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    private boolean inputClosedSeenErrorOnRead;

    protected boolean isInputShutdown0() {
        return false;
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    final boolean shouldBreakReadReady(ChannelConfig config) {
        return isInputShutdown0() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    private static boolean isAllowHalfClosure(ChannelConfig config) {
        return config instanceof SocketChannelConfig &&
                ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    protected class NioByteUnsafe extends AbstractNioUnsafe {

        //处理关闭连接的方法，该方法内部暂且注释一些代码，最后一节课会详细讲解
        private void closeOnRead(ChannelPipeline pipeline) {
            if (!isInputShutdown0()) {
                if (isAllowHalfClosure(config())) {
                    //shutdownInput();
                    //pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            } else {
                inputClosedSeenErrorOnRead = true;
                //pipeline.fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close,
                                         RecvByteBufAllocator.Handle allocHandle) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    readPending = false;
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof IOException) {
                closeOnRead(pipeline);
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:重构之后的读取消息的方法
         */
        @Override
        public final void read() {
            //得到配置类
            final ChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadPending();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            //得到内存分配器，这个是真正的内存分配器
            final ByteBufAllocator allocator = config.getAllocator();
            //得到动态内存分配器的处理器，这个处理器要配合内存分配器来使用
            final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
            //和之前学习的服务端接收客户端连接时一样，下面这个方法同样是用来在本次接收数据之前，重置一下状态。就是把上一次接收到的数据清零
            allocHandle.reset(config);
            ByteBuf byteBuf = null;
            boolean close = false;
            try {
                do {
                    //分配一块内存，注意，如果是第一次来读取字节消息，但是并没有上一次接收到的字节数量做参考，我们怎么知道第一次要分配
                    //多大的ByteBuf呢？所以这里有一个默认值，1024，第一次会分配1024个字节大小，用于接收数据
                    byteBuf = allocHandle.allocate(allocator);
                    //doReadBytes(byteBuf)方法返回的是本次接收到的字节数量。然后把该值记录下来。
                    allocHandle.lastBytesRead(doReadBytes(byteBuf));
                    //这里是得到本次读取到的数据，如果读取到的数据等于0，说明到此为止，客户端channel中的数据已经全部读取完了
                    //可以直接退出循环了
                    if (allocHandle.lastBytesRead() <= 0) {
                        //退出循环之前，释放该ByteBuf
                        byteBuf.release();
                        //清除引用
                        byteBuf = null;
                        //如果本次接收到的结果小于0，就意味着客户端要关闭了，当接收为-1时，代表客户端channel要关闭
                        close = allocHandle.lastBytesRead() < 0;
                        if (close) {
                           //把该属性置为false
                            readPending = false;
                        }
                        break;
                    }
                    //累加读取消息的次数，到达16次就不能再继续读取了。理由之前我们已经分析过了
                    //因为单线程执行器掌管着多个channel，不能把执行事件都给了一个channel，也要给其他channel机会，更要注意
                    //还有很多用户提交的异步任务等待单线程执行器去执行。这个详细的流程可以在我们重构了NioEventLoop的run方法时看到
                    allocHandle.incMessagesRead(1);
                    readPending = false;
                    //回调handler中的channelread方法，该方法也是每读取一次数据就回调一次
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;
                    //判断是否要结束循环了
                } while (allocHandle.continueReading());
                //进行到这里就算是本次读取数据已经完结了，要根据本次读到的所有数据判断下次是否应该扩容
                //也就是下次分配ByteBuf时，是否应该分配一个持有内存更大的ByteBuf
                allocHandle.readComplete();
                //回调channelReadComplete方法，可以看到，该方法在读取完数据之后会被回调，并且只被回调一次
                pipeline.fireChannelReadComplete();
                //下面都是一些和关闭连接相关的操作，暂时不详细展开了
                if (close) {
                    closeOnRead(pipeline);
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close, allocHandle);
            } finally {
                if (!readPending && !config.isAutoRead()) {
                    removeReadOp();
                }
            }
        }
    }

    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    protected final int doWrite0(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg == null) {
            return 0;
        }
        //在这里得到写缓冲区中第一个要发送的数据
        return doWriteInternal(in, in.current());
    }

    /**
     * @Author: PP-jessica
     * @Description:在该方法内判断要发送的是什么类型的数据，然后返回int整数
     */
    private int doWriteInternal(ChannelOutboundBuffer in, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.isReadable()) {
                in.remove();
                return 0;
            }
            //这里把消息从ByteBuf中直接发送到ScoketChannel中
            //注意哦，这里使用的是ByteBuf，并不是ByteBuffer
            final int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                //返回值大于0说明发送成功了
                in.progress(localFlushedAmount);
                if (!buf.isReadable()) {
                    in.remove();
                }
                //这里返回1，指的是发送成功了要返回1，这个1会被写次数减去，这样写次数就从最开始的16变成15，就意味着发送了一次数据
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            //走到这里意味着要发送文件类型的数据
            //这个FileRegion我并没有引入实现类，只引入了一个接口
            //这个不是我们的重点，了解一下就行
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                //这里就是发送成功了
                in.remove();
                //返回0，意味着这次发送并不算作写次数之中
                return 0;
            }
            //零拷贝的方式传输文件类型数据
            long localFlushedAmount = doWriteFileRegion(region);
            if (localFlushedAmount > 0) {
                //走到这里就是发送成功的意思
                in.progress(localFlushedAmount);
                if (region.transferred() >= region.count()) {
                    //走到这里就是发送成功的意思
                    //删除写缓冲区中的链表的首节点
                    in.remove();
                }
                return 1;
            }
        } else {
            throw new Error();
        }
        //走到这里，就意味着localFlushedAmount返回值小于0，说明socket缓冲区满了，不可写，
        //直接返回一个WRITE_STATUS_SNDBUF_FULL值，这个值非常大，写次数减去它后就变成负数了
        //这个负数是有用的，在NioScoketChannel类中会讲到
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        do {
            Object msg = in.current();
            if (msg == null) {
                clearOpWrite();
                return;
            }
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);
        incompleteWrite(writeSpinCount < 0);
    }


    /**
     * @Author: PP-jessica
     * @Description:过滤发送消息类型的方法，因为发送消息时，规定只能发送ByteBuffer或者FileRegion类型包装的msg
     * 这样的消息才会被暂时放到写缓冲区中
     */
    @Override
    protected final Object filterOutboundMessage(Object msg) {
       //判断是否属于ByteBuf类型
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.isDirect()) {
                return msg;
            }
            //这里会发现如果msg不属于ByteBuf，就会用直接内存的ByteBuf来包装msg
            return newDirectBuffer(buf);
        }
        //这里暂且注释掉，不是重点
        //这里就是判断是否属于FileRegion类型
//        if (msg instanceof FileRegion) {
//            return msg;
//        }
        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    /**
     * @Author: PP-jessica
     * @Description:给多路复用器注册write事件的方法
     */
    protected final void incompleteWrite(boolean setOpWrite) {
        if (setOpWrite) {
            //如果setOpWrite为true，就直接向多路复用器注册wirte事件即可
            setOpWrite();
        } else {
            //这里就是达到了16次写次数，但是socket缓冲区依然是可写的情况，这时候就不会注册write事件，而是封装一个flsuh的异步任务
            //提交给单线程执行器去执行
            //这里之所以要先清除write事件，是为了防止程序是从NioEventLoop中的unsafe方法调用了forceFlush走到这里的
            //如果是从NioEventLoop检测到write事件再次走到这里的话，当然就要把write事件移除了，因为现在的情况是socket缓冲区可写了
            clearOpWrite();
            //这里就是提交了一个异步任务，在异步任务中执行了 ((AbstractNioUnsafe) unsafe()).flush0()方法
            //这里也再次反映出了单线程执行器的均衡性，不会把自己交给一个channel无限执行它的发送消息任务
            eventLoop().execute(flushTask);
        }
    }

    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;

    /**
     * @Author: PP-jessica
     * @Description:向多路复用器注册write事件
     */
    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:把监听的write事件从selector上取消掉
     */
    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
}
