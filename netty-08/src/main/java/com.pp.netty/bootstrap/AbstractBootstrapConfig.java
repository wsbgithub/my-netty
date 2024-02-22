package com.pp.netty.bootstrap;

import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFactory;
import com.pp.netty.channel.ChannelOption;
import com.pp.netty.channel.EventLoopGroup;
import com.pp.netty.util.AttributeKey;
import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.StringUtil;

import java.net.SocketAddress;
import java.util.Map;



/**
 * @Author: PP-jessica
 * @Description:我们这门课上到这里，我想应该不用再让我多解释这些抽象类的功能了吧类中没几个方法，随便看看就能明白。，
 */
public abstract class AbstractBootstrapConfig<B extends AbstractBootstrap<B, C>, C extends Channel> {

    protected final B bootstrap;

    protected AbstractBootstrapConfig(B bootstrap) {
        this.bootstrap = ObjectUtil.checkNotNull(bootstrap, "bootstrap");
    }

    public final SocketAddress localAddress() {
        return bootstrap.localAddress();
    }


    @SuppressWarnings("deprecation")
    public final ChannelFactory<? extends C> channelFactory() {
        return bootstrap.channelFactory();
    }



    @SuppressWarnings("deprecation")
    public final EventLoopGroup group() {
        return bootstrap.group();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
                .append(StringUtil.simpleClassName(this))
                .append('(');
        EventLoopGroup group = group();
        if (group != null) {
            buf.append("group: ")
                    .append(StringUtil.simpleClassName(group))
                    .append(", ");
        }
        @SuppressWarnings("deprecation")
        ChannelFactory<? extends C> factory = channelFactory();
        if (factory != null) {
            buf.append("channelFactory: ")
                    .append(factory)
                    .append(", ");
        }
        SocketAddress localAddress = localAddress();
        if (localAddress != null) {
            buf.append("localAddress: ")
                    .append(localAddress)
                    .append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
    }
}
