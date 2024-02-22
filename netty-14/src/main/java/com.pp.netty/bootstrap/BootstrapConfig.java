package com.pp.netty.bootstrap;

import com.pp.netty.channel.Channel;

import java.net.SocketAddress;

public final class BootstrapConfig extends AbstractBootstrapConfig<Bootstrap, Channel> {

    BootstrapConfig(Bootstrap bootstrap) {
        super(bootstrap);
    }


    public SocketAddress remoteAddress() {
        return bootstrap.remoteAddress();
    }


    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", resolver: ");
        SocketAddress remoteAddress = remoteAddress();
        if (remoteAddress != null) {
            buf.append(", remoteAddress: ")
                    .append(remoteAddress);
        }
        return buf.append(')').toString();
    }
}