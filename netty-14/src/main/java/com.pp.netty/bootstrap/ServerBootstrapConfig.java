package com.pp.netty.bootstrap;

import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelOption;
import com.pp.netty.channel.EventLoopGroup;
import com.pp.netty.util.AttributeKey;
import com.pp.netty.util.internal.StringUtil;

import java.util.Map;


public final class ServerBootstrapConfig extends AbstractBootstrapConfig<ServerBootstrap, Channel> {

    ServerBootstrapConfig(ServerBootstrap bootstrap) {
        super(bootstrap);
    }

    @SuppressWarnings("deprecation")
    public EventLoopGroup childGroup() {
        return bootstrap.childGroup();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", ");
        EventLoopGroup childGroup = childGroup();
        if (childGroup != null) {
            buf.append("childGroup: ");
            buf.append(StringUtil.simpleClassName(childGroup));
            buf.append(", ");
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
