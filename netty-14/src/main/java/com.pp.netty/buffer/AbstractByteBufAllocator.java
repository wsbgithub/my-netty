package com.pp.netty.buffer;

import com.pp.netty.util.internal.PlatformDependent;
import com.pp.netty.util.internal.StringUtil;

import static com.pp.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * @Author: PP-jessica
 * @Description:该类没有什么特别需要关注的方法，都是一些模版方法，交给不同的子类去实现
 * 唯一值得重点关注的就是内存泄漏检测器。但是，这节课我们暂且注释掉，下一节课会继续讲解
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {
    //默认分配的内存的初始大小
    static final int DEFAULT_INITIAL_CAPACITY = 256;
    //默认的分配内存的最大值
    static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;
    static final int DEFAULT_MAX_COMPONENTS = 16;
    //ByteBuf扩容时的计算阈值
    static final int CALCULATE_THRESHOLD = 1048576 * 4;

//    static {
//        ResourceLeakDetector.addExclusions(AbstractByteBufAllocator.class, "toLeakAwareBuffer");
//    }
//
//    protected static ByteBuf toLeakAwareBuffer(ByteBuf buf) {
//        ResourceLeakTracker<ByteBuf> leak;
//        switch (ResourceLeakDetector.getLevel()) {
//            case SIMPLE:
//                leak = AbstractByteBuf.leakDetector.track(buf);
//                if (leak != null) {
//                    buf = new SimpleLeakAwareByteBuf(buf, leak);
//                }
//                break;
//            case ADVANCED:
//            case PARANOID:
//                leak = AbstractByteBuf.leakDetector.track(buf);
//                if (leak != null) {
//                    buf = new AdvancedLeakAwareByteBuf(buf, leak);
//                }
//                break;
//            default:
//                break;
//        }
//        return buf;
//    }

//    protected static CompositeByteBuf toLeakAwareBuffer(CompositeByteBuf buf) {
//        ResourceLeakTracker<ByteBuf> leak;
//        switch (ResourceLeakDetector.getLevel()) {
//            case SIMPLE:
//                leak = AbstractByteBuf.leakDetector.track(buf);
//                if (leak != null) {
//                    buf = new SimpleLeakAwareCompositeByteBuf(buf, leak);
//                }
//                break;
//            case ADVANCED:
//            case PARANOID:
//                leak = AbstractByteBuf.leakDetector.track(buf);
//                if (leak != null) {
//                    buf = new AdvancedLeakAwareCompositeByteBuf(buf, leak);
//                }
//                break;
//            default:
//                break;
//        }
//        return buf;
//    }

    private final boolean directByDefault;
    //private final ByteBuf emptyBuf;
    private  ByteBuf emptyBuf;


    protected AbstractByteBufAllocator() {
        this(false);
    }


    protected AbstractByteBufAllocator(boolean preferDirect) {
        //这里directByDefault被赋值为true
        directByDefault = preferDirect && PlatformDependent.hasUnsafe();
        //这里暂且注释掉
        //emptyBuf = new EmptyByteBuf(this);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf buffer() {
        //这里为true，就创建一个直接内存的buffer
        if (directByDefault) {
            return directBuffer();
        }
        return heapBuffer();
    }


    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf ioBuffer() {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(DEFAULT_INITIAL_CAPACITY);
        }
        return heapBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，也许是基于堆内存，也许是基于直接内存
     */
    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
        if (PlatformDependent.hasUnsafe() || isDirectBufferPooled()) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于堆内存
     */
    @Override
    public ByteBuf heapBuffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于堆内存
     */
    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于堆内存
     */
    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于直接内存
     */
    @Override
    public ByteBuf directBuffer() {
        return directBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于直接内存
     */
    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个ByteBuf，基于直接内存
     */
    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newDirectBuffer(initialCapacity, maxCapacity);
    }
//和CompositeByteBuf有关的暂时注释掉
//    @Override
//    public CompositeByteBuf compositeBuffer() {
//        if (directByDefault) {
//            return compositeDirectBuffer();
//        }
//        return compositeHeapBuffer();
//    }
//
//    @Override
//    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
//        if (directByDefault) {
//            return compositeDirectBuffer(maxNumComponents);
//        }
//        return compositeHeapBuffer(maxNumComponents);
//    }
//
//    @Override
//    public CompositeByteBuf compositeHeapBuffer() {
//        return compositeHeapBuffer(DEFAULT_MAX_COMPONENTS);
//    }
//
//    @Override
//    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
//        return toLeakAwareBuffer(new CompositeByteBuf(this, false, maxNumComponents));
//    }
//
//    @Override
//    public CompositeByteBuf compositeDirectBuffer() {
//        return compositeDirectBuffer(DEFAULT_MAX_COMPONENTS);
//    }
//
//    @Override
//    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
//        return toLeakAwareBuffer(new CompositeByteBuf(this, true, maxNumComponents));
//    }

    private static void validate(int initialCapacity, int maxCapacity) {
        checkPositiveOrZero(initialCapacity, "initialCapacity");
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: not greater than maxCapacity(%d)",
                    initialCapacity, maxCapacity));
        }
    }


    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);


    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(directByDefault: " + directByDefault + ')';
    }

    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyangjj。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/8/22
     * @Description:扩容的核心方法
     */
    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity");
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                    minNewCapacity, maxCapacity));
        }
        //CALCULATE_THRESHOLD就是4M
        final int threshold = CALCULATE_THRESHOLD;

        if (minNewCapacity == threshold) {
            return threshold;
        }
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }
        int newCapacity = 64;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        return Math.min(newCapacity, maxCapacity);
    }
}

