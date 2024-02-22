package com.pp.netty.channel;

import com.pp.netty.util.AbstractConstant;
import com.pp.netty.util.ConstantPool;

import java.net.InetAddress;
import java.net.NetworkInterface;


/**
 * @Author: PP-jessica
 * @Description:我们真正使用的常量类，这里面我删除了一些方法，都是被作者废弃了的方法
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        //ConstantPool抽象类中的抽象方法，在这里得到了实现
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };


    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) pool.valueOf(name);
    }


    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }


    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * @Author: PP-jessica
     * @Description:在源码中，下面的这些属性都是作者已经创建好的常量，看看有没有你熟悉的。
     */
    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");
    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");
    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
    /**
     * @Author: PP-jessica
     * @Description:记得我们给channel配置的参数吗option(ChannelOption.SO_BACKLOG,128)，是不是很熟悉，我们拿来即用的常量，因为作者
     * 已经创建好了。这里我多说一句，不要被ChannelOption<T>中的泛型给迷惑了，觉得ChannelOption中也存储着
     * 用户定义的值，就是那个泛型的值，比如说option(ChannelOption.SO_BACKLOG,128)里面的128，以为ChannelOption<Integer>中的integer
     * 存储的就是128，实际上128存储在serverbootstrap的linkmap中。而作者之所以给常量类设定泛型，是因为Attribut会存储泛型的值，这个我们在这节课
     * 就会讲到
     */
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");
    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");


    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP =
            valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    @Deprecated
    protected ChannelOption(String name) {
        this(pool.nextId(), name);
    }

    private ChannelOption(int id, String name) {
        super(id, name);
    }

    public void validate(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }
}
