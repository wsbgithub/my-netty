package com.pp.netty.channel;

import com.pp.netty.buffer.ByteBuf;

import static com.pp.netty.util.internal.ObjectUtil.checkPositiveOrZero;


public final class DefaultMessageSizeEstimator implements MessageSizeEstimator {

    private static final class HandleImpl implements Handle {
        private final int unknownSize;

        private HandleImpl(int unknownSize) {
            this.unknownSize = unknownSize;
        }

        @Override
        public int size(Object msg) {
            if (msg instanceof ByteBuf) {
                //得到ByteBuf中的可读字节，可读字节不就是ByteBuf的大小吗？
                return ((ByteBuf) msg).readableBytes();
            }
            //下面的代码暂且注释掉
//            if (msg instanceof ByteBufHolder) {
//                return ((ByteBufHolder) msg).content().readableBytes();
//            }
//            if (msg instanceof FileRegion) {
//                return 0;
//            }
            return unknownSize;
        }
    }


    public static final MessageSizeEstimator DEFAULT = new DefaultMessageSizeEstimator(8);

    private final Handle handle;


    public DefaultMessageSizeEstimator(int unknownSize) {
        checkPositiveOrZero(unknownSize, "unknownSize");
        handle = new HandleImpl(unknownSize);
    }

    @Override
    public Handle newHandle() {
        return handle;
    }
}
