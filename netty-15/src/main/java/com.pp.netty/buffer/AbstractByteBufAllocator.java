package com.pp.netty.buffer;

import com.pp.netty.util.ResourceLeakDetector;
import com.pp.netty.util.ResourceLeakTracker;
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
    static final int CALCULATE_THRESHOLD = 1048576 * 4; // 4 MiB page


    static {
        ResourceLeakDetector.addExclusions(AbstractByteBufAllocator.class, "toLeakAwareBuffer");
    }

    //在这里，我还是要啰嗦几句，为大家详细解释一下，内存泄露是怎么发生的，以及会如何被检测到
    //首先，在netty中如果使用的是非池化的堆内存，则是不需要关注内存泄露的，因为垃圾回收机制会为我们自动回收内存
    //但是如果使用的是可池化的直接内存，学完内存池，大家应该对这些内存都有清楚的认识了，使用的如果是这样的内存，就不得不关注内存泄露了。
    //因为这样的内存要不然就要放到内存池，要不然就要归还给Chunk，总之，在持有这块内存的Bytebuf做完操作后，一定要对这块直接内存做出相应的
    //操作，保证这块内存还可以被再次利用。这时候问题就来了，我们使用的是可池化的直接内存，但是包装这块直接内存的Bytebuf使用的可并不是直接内存
    //而是堆内存，既然是堆内存，就会被垃圾回收机制掌控如果ByteBuf被垃圾回收了，但是该Bytebuf持有的内存却并未释放，这就会造成内存泄露问题
    //所以要怎么做？把ByteBuf交给弱引用对象，如果ByteBuf被垃圾回收了，但是弱引用对象中的ByteBuf引用并没有被清除，那该弱引用对象就会被放到
    //一个弱引用队列中，Netty就可以检查该弱引用队列来判断是否出现内存泄漏了。只有被包装过ByteBuf执行了release方法，才会讲自身从对应的
    //弱引用对象中清除，这样被垃圾回收后弱引用对象也不会被放到弱引用队列中。而在执行release方法的过程中，还会释放自身持有的内存。由此做到了内存泄漏的检测
    protected static ByteBuf toLeakAwareBuffer(ByteBuf buf) {
        ResourceLeakTracker<ByteBuf> leak;
        //获取配置的内存泄漏检测级别，根据不同的级别会返回
        //不同的ByteBuf包装类，即下面的SimpleLeakAwareByteBuf
        //或者AdvancedLeakAwareByteBuf
        switch (ResourceLeakDetector.getLevel()) {
            case SIMPLE:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new SimpleLeakAwareByteBuf(buf, leak);
                }
                break;
            case ADVANCED:
            case PARANOID:
                leak = AbstractByteBuf.leakDetector.track(buf);
                if (leak != null) {
                    buf = new AdvancedLeakAwareByteBuf(buf, leak);
                }
                break;
            default:
                break;
        }
        return buf;
    }

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

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity");
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                    minNewCapacity, maxCapacity));
        }
        final int threshold = CALCULATE_THRESHOLD; // 4 MiB page

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

