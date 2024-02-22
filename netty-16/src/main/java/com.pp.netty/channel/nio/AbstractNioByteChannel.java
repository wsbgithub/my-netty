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

public abstract class AbstractNioByteChannel extends AbstractNioChannel{

    //就是在这里把每次接收数据的最大次数设定为16了
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

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
                    //在lastBytesRead方法中也会判断，如果这一次循环中接收到的字节把ByteBuf填满了
                    //那么下一次循环会分配一个更大的ByteBuf对象，也就是扩容了
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


    @Override
    protected void doWrite(Object masg) throws Exception {

    }
}
