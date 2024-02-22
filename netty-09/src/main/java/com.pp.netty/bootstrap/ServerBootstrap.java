package com.pp.netty.bootstrap;

import com.pp.netty.channel.*;
import com.pp.netty.util.AttributeKey;
import com.pp.netty.util.concurrent.EventExecutor;
import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.SocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, Channel> {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);
    /**
     * @Author: PP-jessica
     * @Description:用户设定的NioSocketChannel的参数会暂时存放在这个map中，channel初始化的时候，这里面的数据才会存放到channel的配置类中
     */
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    /**
     * @Author: PP-jessica
     * @Description:用户设定的NioSocketChannel的参数会暂时存放在这个map中，channel初始化的时候，这里面的数据才会存放到channel的配置类中
     */
    private final Map<AttributeKey<?>, Object> childAttrs = new LinkedHashMap<AttributeKey<?>, Object>();

    private final ServerBootstrapConfig config = new ServerBootstrapConfig(this);

    private EventLoopGroup childGroup;

    private volatile ChannelFactory<? extends Channel> channelFactory;

    public ServerBootstrap() {

    }

    private ServerBootstrap(ServerBootstrap bootstrap) {
        super(bootstrap);
        childGroup = bootstrap.childGroup;
        synchronized (bootstrap.childOptions) {
            childOptions.putAll(bootstrap.childOptions);
        }
        synchronized (bootstrap.childAttrs) {
            childAttrs.putAll(bootstrap.childAttrs);
        }
    }

    @Override
    public ServerBootstrap group(EventLoopGroup group) {
        return group(group, group);
    }

    /**
     * @Author: PP-jessica
     * @Description:把boss线程组和工作线程组赋值给属性，并且把boss线程组传递到父类，这时候线程组都已经初始化完毕了
     * 里面的每个线程执行器也都初始化完毕
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        ObjectUtil.checkNotNull(childGroup, "childGroup");
        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }


    public <T> ServerBootstrap childOption(ChannelOption<T> childOption, T value) {
        ObjectUtil.checkNotNull(childOption, "childOption");
        if (value == null) {
            synchronized (childOptions) {
                childOptions.remove(childOption);
            }
        } else {
            synchronized (childOptions) {
                childOptions.put(childOption, value);
            }
        }
        return this;
    }

    public <T> ServerBootstrap childAttr(AttributeKey<T> childKey, T value) {
        ObjectUtil.checkNotNull(childKey, "childKey");
        if (value == null) {
            childAttrs.remove(childKey);
        } else {
            childAttrs.put(childKey, value);
        }
        return this;
    }


    @Override
    void init(Channel channel) throws Exception {
        //得到所有存储在map中的用户设定的channel的参数
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            //把初始化时用户配置的参数全都放到channel的config类中，因为没有引入netty源码的打印日志模块，
            //所以就把该方法修改了，去掉了日志参数
            setChannelOptions(channel, options);
        }
        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Map.Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                @SuppressWarnings("unchecked")
                AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
                channel.attr(key).set(e.getValue());
            }
        }
        //在这里创建channel的ChannelPipeline，也就是ChannelHandler链表
        ChannelPipeline p = channel.pipeline();
        //这里要把用户设置的服务端的ChannelHandler添加到ChannelPipeline中
        //不过这里逻辑简化了一些，因为还有一些东西没引入，等下节课会继续讲解，到时候我们
        //会实现真正的逻辑，这里为了测试handler，我们先简单写一下，源码中的基本逻辑也是这样的
        //注意这里是添加到尾节点的前面，因为执行bind方法的出站处理器，出站处理器是从后向前传递数据。从尾节点开始，
        //传递到尾节点的前一个节点，然后为节点的前一个节点
        p.addLast(config.handler());
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<AttributeKey<?>, Object>[] newAttrArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<ChannelOption<?>, Object>[] newOptionArray(int size) {
        return new Map.Entry[size];
    }

    @Override
    public ServerBootstrap validate() {
        super.validate();
        //还没有引入channelHandler，先把这一段注释掉
//        if (childHandler == null) {
//            throw new IllegalStateException("childHandler not set");
//        }
        if (childGroup == null) {
            logger.warn("childGroup is not set. Using parentGroup instead.");
            childGroup = config.group();
        }
        return this;
    }

    @Deprecated
    public EventLoopGroup childGroup() {
        return childGroup;
    }

    @Override
    public final ServerBootstrapConfig config() {
        return config;
    }
}
