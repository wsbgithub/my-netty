package com.pp.netty.channel.socket.nio;


import com.pp.netty.buffer.ByteBuf;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.ChannelOption;
import com.pp.netty.channel.RecvByteBufAllocator;
import com.pp.netty.channel.nio.AbstractNioByteChannel;
import com.pp.netty.channel.socket.DefaultSocketChannelConfig;
import com.pp.netty.channel.socket.SocketChannelConfig;
import com.pp.netty.util.internal.SocketUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;

/**
 * @Author: PP-jessica
 * @Description:对socketchannel做了一层包装，同时也因为channel接口和抽象类的引入，终于可以使NioEventLoop和channel解耦了
 */
public class NioSocketChannel extends AbstractNioByteChannel {

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    private static SocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a socket.", e);
        }
    }

    private final SocketChannelConfig config;

    /**
     * @Author: PP-jessica
     * @Description:无参构造器，netty客户端初始化的时候，channel工厂反射调用的就是这个构造器
     */
    public NioSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    public NioSocketChannel(SelectorProvider provider) {
        this(newSocket(provider));
    }

    public NioSocketChannel(SocketChannel socket) {
        this(null, socket);
    }

    public NioSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
        config = new NioSocketChannelConfig(this, socket.socket());
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        //channel是否为Connected状态，是客户端channel判断是否激活的条件。
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }


    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }


    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    /**
     * @Author: PP-jessica
     * @Description:这里是一个系统调用方法，判断当前的java版本是否为7以上，这里我就直接写死了，默认用的都是java8，不引入更多的工具类了
     */
    private void doBind0(SocketAddress localAddress) throws Exception {
            SocketUtils.bind(javaChannel(), localAddress);
    }


    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        //从这里可以看出，如果连接的时候把本地地址也传入了，那么就要在连接远端的同时，监听本地端口号
        if (localAddress != null) {
            doBind0(localAddress);
        }
        boolean success = false;
        try {
            //SocketUtils.connect(javaChannel(), remoteAddress)该方法如果连接成功就直接返回true
            //如果没有接收到服务端的ack就会返回false，但并不意味着该方法就彻底失败了，有可能ack在路上等等，最终需要注册连接事件来监听结果
            //这会让AbstractNioChannel类中的connect方法进入到添加定时任务的分支，如果超过设定的时间一直没有连接成功，就会在客户端报错
            //如果连接成功了，连接成功的时候会把该定时任务中的某些变量置为null,现在我们还没有加入定时任务
            //这里会返回false，所以逻辑会走到下面的分支
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                //逻辑肯定会走到这里
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
        //看，在这里就能拿到socketchannel并且发送消息成功！说明问题并不是出在连接上，一定是哪里没有阻塞住，仔细想想netty的线程模型
//        SocketChannel socketChannel = javaChannel();
//        socketChannel.write(ByteBuffer.wrap("我是真正的netty！".getBytes()));
//        System.out.println("数据发送了！");
    }


    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    /**
     * @Author: PP-jessica
     * @Description:重构之后的读取消息的终极方法
     */
    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        //得到动态内存分配器的处理器
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        //这里先得到ByteBuf的可写字节数，然后将这个可写字节数赋值给处理器中的attemptedBytesRead属性
        //为什么要这么做？因为最后读取到的字节数和这个可以入的字节数相等了，说明这次读取数据已经满了，ByteBuf已经装不下数据了
        //但是这并不意味着channel中就没有可读取的数据了，这只能说明这个ByteBuf没办法再写入数据了
        //如果是另一种结果，就是最后读取到的字节数小于这个可写入的字节数，说明channel中的数据已经全部读取完了
        //总之，这个属性被赋值了，就可以很容易判断出读取了之后，客户端channel中是否还有数据可以被读取
        //这个byteBuf.writableBytes()的可写入字节数每次都是会变化的，这个要弄清楚
        allocHandle.attemptedBytesRead(byteBuf.writableBytes());
        //在这里把客户端channel和可写的字节数传进方法内，数据是要从客户端channel中写入到ByteBuf中的
        //这里就会把数据从channel写到ByteBuf中了
        return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());
    }

    @Override
    protected void doWrite(Object msg) throws Exception {
        //真正发送数据的时候到了，这时候就不能用NioSocketChannel了，要用java原生的socketchannel
        SocketChannel socketChannel = javaChannel();
        //转换数据类型
        ByteBuffer buffer = (ByteBuffer)msg;
        //发送数据
        socketChannel.write(buffer);
    }


    /**
     * @Author: PP-jessica
     * @Description:用户设置的客户端channel的参数由此类进行设置，这里面有的方法现在还不需要是用来干什么的，等学完ByteBuf了会全部讲解
     */
    private final class NioSocketChannelConfig extends DefaultSocketChannelConfig {
        private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;
        private NioSocketChannelConfig(NioSocketChannel channel, Socket javaSocket) {
            super(channel, javaSocket);
            calculateMaxBytesPerGatheringWrite();
        }

        @Override
        public NioSocketChannelConfig setSendBufferSize(int sendBufferSize) {
            super.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
            return this;
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            if ( option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            }
            return super.setOption(option, value);
        }

        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }
            return super.getOption(option);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(super.getOptions(), NioChannelOption.getOptions(jdkChannel()));
        }

        void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
            this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
        }

        int getMaxBytesPerGatheringWrite() {
            return maxBytesPerGatheringWrite;
        }

        private void calculateMaxBytesPerGatheringWrite() {
            int newSendBufferSize = getSendBufferSize() << 1;
            if (newSendBufferSize > 0) {
                setMaxBytesPerGatheringWrite(getSendBufferSize() << 1);
            }
        }

        private SocketChannel jdkChannel() {
            return ((NioSocketChannel) channel).javaChannel();
        }
    }
}
