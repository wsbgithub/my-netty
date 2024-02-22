package com.pp.netty.channel;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.util.ReferenceCountUtil;
import com.pp.netty.util.concurrent.EventExecutor;
import com.pp.netty.util.concurrent.EventExecutorGroup;
import com.pp.netty.util.internal.ObjectUtil;
import com.pp.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;


/**
 * @Author: PP-jessica
 * @Description:默认的ChannelPipeline实现类,该类引入了很多方法，因为我是直接从源码中复制过来的，很多方法不需要做改动，直接加上注释就行
 * 有的方法做了一些改动，这样就可以不引入更多的类。虽然引入了太多方法，但是很多方法在我们手写的项目中都用不上，摆在这里是凑个数，真要删减，
 * 还得从接口开始搞，兄弟们，我就先不弄了。。
 */
public class DefaultChannelPipeline implements ChannelPipeline{

    private static final Logger logger = LoggerFactory.getLogger(DefaultChannelPipeline.class);
    /**
     * @Author: PP-jessica
     * @Description:把DefaultChannelPipeline当成一个链表的话，链表中就要有头节点和尾节点
     * 下面的两个属性就是头节点和尾节点的名字
     */
    private static final String HEAD_NAME = generateName0(HeadContext.class);
    private static final String TAIL_NAME = generateName0(TailContext.class);

    /**
     * @Author: PP-jessica
     * @Description:本来引入了channelhandler，就要引入这个netty作者自己实现的FastThreadLocal
     *但我们这里先不引入，先讲完核心的地方。实际上这个很简单，无非就是java原生用ThreadLocalMap用hashmap那一套存储数据，
     * 而作者定义的则拥有一个初始化好的数组下标，根据下标存储数据到数组中，不会因为hash冲突浪费继续寻找空位的时间，感兴趣的同学可以点开源码看一看，如果前面的那些课程你都懂了，你会发现，netty
     * 的许多源码你自己都可以看明白了。这里我们就先不引入了，用原生的java的ThreadLocal代替
     */
//    private static final FastThreadLocal<Map<Class<?>, String>> nameCaches =
//            new FastThreadLocal<Map<Class<?>, String>>() {
//                @Override
//                protected Map<Class<?>, String> initialValue() {
//                    return new WeakHashMap<Class<?>, String>();
//                }
//            };
    private static final ThreadLocal<Map<Class<?>, String>> nameCaches =
            //nameCaches中缓存着每个ChannelHandler的名字
              new ThreadLocal<Map<Class<?>, String>>() {
                   @Override
                   protected Map<Class<?>, String> initialValue() {
                      return new WeakHashMap<Class<?>, String>();
                }
            };



    /**
     * @Author: PP-jessica
     * @Description:把DefaultChannelPipeline当成一个链表的话，下面两个就是头节点和尾节点
     */
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;
    /**
     * @Author: PP-jessica
     * @Description:链表中有channel，还记得我们之前讲到AttributeMap吗？channel本身就是一个map，这意味着用户向channel
     * 中存储了数据，那么只要通过DefaultChannelPipeline得到了channel，就可以得到channel中的数据。先记住这一点，讲到AbstractChannelHandlerContext
     * 的时候就知道用法了
     */
    private final Channel channel;

    private  ChannelFuture succeededFuture;

    /**
     * @Author: PP-jessica
     * @Description:为true则是第一次注册
     */
    private boolean firstRegistration = true;

    /**
     * @Author: PP-jessica
     * @Description:这是一个非常重要的任务链表，在向DefaultChannelPipeline中添加handler时，会用到这个链表
     * 根据变量的名字可以知道，这明显是链表的头节点
     */
    private PendingHandlerCallback pendingHandlerCallbackHead;

    /**
     * @Author: PP-jessica
     * @Description:Channel是否注册成功，这里指的是是否注册单线程执行器成功
     */
    private boolean registered;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        //因为我没有引入CompleteFuture，所以这一行先注释了
        //succeededFuture = new DefaultChannelPromise(channel, null);
        tail = new TailContext(this);
        head = new HeadContext(this);
        head.next = tail;
        tail.prev = head;
    }

    /**
     * @Author: PP-jessica
     * @Description:创建一个上下文，也就是创建一个链表中的节点
     */
    private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
    }

    /**
     * @Author: PP-jessica
     * @Description:简化一下该方法
     */
    private EventExecutor childExecutor(EventExecutorGroup group) {
        if (group == null) {
            return null;
        }
        return null;
    }

    @Override
    public final Channel channel() {
        return channel;
    }

    /**
     * @Author: PP-jessica
     * @Description:把channelhandler添加到链表的首位，也就是头节点的后面
     */
    @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            //检查该ChannelHandler是否可以共享，也就是能否被重复添加，能被多个ChannelPipeline共享的handler都会加上@Sharable注解
            checkMultiplicity(handler);
            //判断handler的名字是否已经存在，如果存在就为该handler创建新的名字
            name = filterName(name, handler);
            //把ChannelHandler封装在ChannelHandlerContext对象中
            newCtx = newContext(group, name, handler);
            //执行添加方法，注意，其实在AbstractChannelHandlerContext类中有几个属性和ChannelHandler
            //的添加状态有关，仅仅是添加到链表中时，handler还无法真正处理数据，直到handler的状态更新为添加完成时
            //该handler才可以处理数据，那怎样才嫩让hanndler的添加状更新呢？就是在channelHandler的handlerAdded方法被回调后
            //其状态会更新为ADD_COMPLETE
            addFirst0(newCtx);
            //到这个时候，channel其实还没有注册到单线程执行器上，所哟代码一定会进行到这个分支内
            if (!registered) {
                //设定ChannelHandler的添加状态为ADD_PENDING
                newCtx.setAddPending();
                //这里是向由PendingHandlerCallback类型的对象构成的链表中添加节点，节点实现了Runnable接口，当channel注册单线程
                //执行器成功后，由PendingHandlerCallback对象构成的链表会回调每一个节点中的run方法，而在run方法中，会回调每一个handler
                //的handlerAdded方法，之后handler的添加状态会更新为ADD_COMPLETE
                //到这里我们可以捋一下，首先每一个channel在初始化时会初始化ChannelPipeline，也就是说，每一个channel都对应着一个ChannelPipeline
                //而每个ChannelPipeline链表中会添加很多ChannelHandler，每个Channelhandler都有handlerAdded方法，当这些handler被添加进
                //链表的时候，会同时把自己封装进PendingHandlerCallback对象中，作为PendingHandlerCallback对象构成的链表的一部分
                //最后触发一系列回调，更新handler的添加状态，然后可以处理channel中的数据。
                //学完了这一块，就会明白，可以说回调是ChannelPipeline中的精髓！弄明白ChannelHandler中的各种方法的回调时机和回调位置，对我们游刃有余
                //地使用netty和二次开发netty至关重要！
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
            //得到单线程执行器
            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                //封装成异步任务，让单线程执行器执行
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        //执行回调函数，实际上就是执行ChannelHandler中的handlerAddded方法
        callHandlerAdded0(newCtx);
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:真正添加handler的方法，更改链表中的指针指向
     */
    private void addFirst0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
    }

    /**
     * @Author: PP-jessica
     * @Description:有了上面的例子，下面这个last就是添加到链表末端的方法，也就是尾节点之前，就不再分析那么详细了
     */
    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            checkMultiplicity(handler);
            newCtx = newContext(group, filterName(name, handler), handler);
            addLast0(newCtx);
            if (!registered) {
                //第一次注册channel的话逻辑会直接走到这里。封装回调链表
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
            EventExecutor executor = newCtx.executor();
            //如果不是单线程执行器的线程，就封装成异步任务，由单线程执行器执行回调
            if (!executor.inEventLoop(Thread.currentThread())) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        //如果走到这里，说明channel已经注册成功了，但是这时候又向ChannelPipeline中添加了handler，这时候就可以直接执行回调了
        callHandlerAdded0(newCtx);
        return this;
    }

    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }

    /**
     * @Author: PP-jessica
     * @Description:在某个Channelhandler前面添加handler，这里多解释一下，我记得当我最初学习tomcat的过滤链的时候，当时存储过滤器用
     * 的是数组，那个也是责任链模式。但是netty中为什么选择用链表来实现责任链模式呢？原因很简单，如果频繁的插入数据，链表会比数组效率更高
     * 这些方法就不讲的那么详细了，下面有很多方法都是直接把源码复制过来了，很简单，点进去一看就明白。
     */
    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addBefore(
            EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        final AbstractChannelHandlerContext ctx;
        synchronized (this) {
            checkMultiplicity(handler);
            name = filterName(name, handler);
            ctx = getContextOrDie(baseName);
            newCtx = newContext(group, name, handler);
            addBefore0(ctx, newCtx);
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    private static void addBefore0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx.prev;
        newCtx.next = ctx;
        ctx.prev.next = newCtx;
        ctx.prev = newCtx;
    }

    private String filterName(String name, ChannelHandler handler) {
        if (name == null) {
            //如果没有指定handler的名字，会生成一个。
            return generateName(handler);
        }
        //到这里说明用户设定了名字，这时候就要判断名字的唯一性
        checkDuplicateName(name);
        return name;
    }

    @Override
    public final ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addAfter(
            EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        final AbstractChannelHandlerContext ctx;
        synchronized (this) {
            checkMultiplicity(handler);
            name = filterName(name, handler);
            ctx = getContextOrDie(baseName);
            newCtx = newContext(group, name, handler);
            addAfter0(ctx, newCtx);
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    private static void addAfter0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx;
        newCtx.next = ctx.next;
        ctx.next.prev = newCtx;
        ctx.next = newCtx;
    }

    public final ChannelPipeline addFirst(ChannelHandler handler) {
        return addFirst(null, handler);
    }

    @Override
    public final ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        if (handlers.length == 0 || handlers[0] == null) {
            return this;
        }
        int size;
        for (size = 1; size < handlers.length; size ++) {
            if (handlers[size] == null) {
                break;
            }
        }
        for (int i = size - 1; i >= 0; i --) {
            ChannelHandler h = handlers[i];
            addFirst(executor, null, h);
        }
        return this;
    }

    public final ChannelPipeline addLast(ChannelHandler handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }
        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, null, h);
        }
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:给ChannelHandler生成名字，并且确保该名字不会重复
     */
    private String generateName(ChannelHandler handler) {
        //获取缓存着每个handler名字的map
        Map<Class<?>, String> cache = nameCaches.get();
        Class<?> handlerType = handler.getClass();
        String name = cache.get(handlerType);
        if (name == null) {
            //handler没有名字，直接生成一个
            name = generateName0(handlerType);
            cache.put(handlerType, name);
        }
        if (context0(name) != null) {
            //走到这说明名字重复了，就创建新的
            String baseName = name.substring(0, name.length() - 1);
            for (int i = 1;; i ++) {
                String newName = baseName + i;
                if (context0(newName) == null) {
                    name = newName;
                    break;
                }
            }
        }
        return name;
    }

    /**
     * @Author: PP-jessica
     * @Description:生成handler的名字
     */
    private static String generateName0(Class<?> handlerType) {
        return StringUtil.simpleClassName(handlerType) + "#0";
    }

    /**
     * @Author: PP-jessica
     * @Description:从链表中删除一个节点
     */
    @Override
    public final ChannelPipeline remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:从链表中删除一个节点，并得到删除节点中的handler
     */
    @Override
    public final ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).handler();
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelHandler> T removeIfExists(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }
        return (T) remove((AbstractChannelHandlerContext) ctx).handler();
    }

    /**
     * @Author: PP-jessica
     * @Description:删除节点的方法
     */
    private AbstractChannelHandlerContext remove(final AbstractChannelHandlerContext ctx) {
        //判断不是头节点和尾节点
        assert ctx != head && ctx != tail;
        synchronized (this) {
            //删除链表中对应的ChannelHandlerContext
            remove0(ctx);
            if (!registered) {
                //这里跟添加节点是有区别的，这里添加链表节点要是执行回调的话，最终执行的是每个handler中的HandlerRemoved方法
                callHandlerCallbackLater(ctx, false);
                return ctx;
            }
            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerRemoved0(ctx);
                    }
                });
                return ctx;
            }
        }
        //进行到这里时，说明channel已经注册成功了，handler可以直接被调用，只要有节点从ChannelPipeline中被删除了，就会回调该handler的
        //handlerRemoved方法
        callHandlerRemoved0(ctx);
        return ctx;
    }

    private static void remove0(AbstractChannelHandlerContext ctx) {
        AbstractChannelHandlerContext prev = ctx.prev;
        AbstractChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
    }

    @Override
    public final ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next).handler();
    }

    @Override
    public final ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev).handler();
    }

    @Override
    public final ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
        return this;
    }

    @Override
    public final ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T replace(
            Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    private ChannelHandler replace(
            final AbstractChannelHandlerContext ctx, String newName, ChannelHandler newHandler) {
        assert ctx != head && ctx != tail;
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            checkMultiplicity(newHandler);
            if (newName == null) {
                newName = generateName(newHandler);
            } else {
                boolean sameName = ctx.name().equals(newName);
                if (!sameName) {
                    checkDuplicateName(newName);
                }
            }
            newCtx = newContext(ctx.executor, newName, newHandler);
            replace0(ctx, newCtx);
            if (!registered) {
                callHandlerCallbackLater(newCtx, true);
                callHandlerCallbackLater(ctx, false);
                return ctx.handler();
            }
            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerAdded0(newCtx);
                        callHandlerRemoved0(ctx);
                    }
                });
                return ctx.handler();
            }
        }
        callHandlerAdded0(newCtx);
        callHandlerRemoved0(ctx);
        return ctx.handler();
    }

    private static void replace0(AbstractChannelHandlerContext oldCtx, AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = oldCtx.prev;
        AbstractChannelHandlerContext next = oldCtx.next;
        newCtx.prev = prev;
        newCtx.next = next;

        prev.next = newCtx;
        next.prev = newCtx;

        oldCtx.prev = newCtx;
        oldCtx.next = newCtx;
    }


    /**
     * @Author: PP-jessica
     * @Description:判断该channelhandler是否可以重复添加
     */
    private static void checkMultiplicity(ChannelHandler handler) {
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
            //标注@Sharable注解的handler才可以添加到不同的ChannelPipeline中
            //要考虑到多线程执行的情况，一个处理器如果添加到不同的ChannelPipeline中，
            //被不同的执行器去处理事件，就有可能发生并发问题
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(
                        h.getClass().getName() +
                                " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
    }

    private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
        try {
            //开始执行回调任务了
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                remove0(ctx);
                ctx.callHandlerRemoved();
                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }
            if (removed) {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                                ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new ChannelPipelineException(
                        ctx.handler().getClass().getName() +
                                ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    private void callHandlerRemoved0(final AbstractChannelHandlerContext ctx) {
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new ChannelPipelineException(
                    ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个方法相当重要，先混个眼熟，后面讲到channel注册成功的时候，会执行它，然后回调链表中的节点的方法
     */
    final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop(Thread.currentThread());
        //这里是为了保证下面的方法只被执行一次
        if (firstRegistration) {
            firstRegistration = false;
            //还记得这个PendingHandlerAddedTask吧？这里就要开始执行它的run方法，然后回调每一个ChannelHandler的handlerAdded方法
            callHandlerAddedForAllHandlers();
        }
    }

    @Override
    public final ChannelHandler first() {
        ChannelHandlerContext first = firstContext();
        if (first == null) {
            return null;
        }
        return first.handler();
    }

    @Override
    public final ChannelHandlerContext firstContext() {
        AbstractChannelHandlerContext first = head.next;
        if (first == tail) {
            return null;
        }
        return head.next;
    }

    @Override
    public final ChannelHandler last() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last.handler();
    }

    @Override
    public final ChannelHandlerContext lastContext() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last;
    }

    @Override
    public final ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        if (ctx == null) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (ctx == null) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public final ChannelHandlerContext context(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return context0(name);
    }

    @Override
    public final ChannelHandlerContext context(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        AbstractChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                return null;
            }
            if (ctx.handler() == handler) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public final ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        if (handlerType == null) {
            throw new NullPointerException("handlerType");
        }
        AbstractChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                return null;
            }
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public final List<String> names() {
        List<String> list = new ArrayList<String>();
        AbstractChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == null) {
                return list;
            }
            list.add(ctx.name());
            ctx = ctx.next;
        }
    }

    @Override
    public final Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<String, ChannelHandler>();
        AbstractChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == tail) {
                return map;
            }
            map.put(ctx.name(), ctx.handler());
            ctx = ctx.next;
        }
    }

    @Override
    public final Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }


    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder()
                .append(StringUtil.simpleClassName(this))
                .append('{');
        AbstractChannelHandlerContext ctx = head.next;
        for (;;) {
            if (ctx == tail) {
                break;
            }

            buf.append('(')
                    .append(ctx.name())
                    .append(" = ")
                    .append(ctx.handler().getClass().getName())
                    .append(')');

            ctx = ctx.next;
            if (ctx == tail) {
                break;
            }

            buf.append(", ");
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public final ChannelPipeline fireChannelRegistered() {
        AbstractChannelHandlerContext.invokeChannelRegistered(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelUnregistered() {
        AbstractChannelHandlerContext.invokeChannelUnregistered(head);
        return this;
    }


    private synchronized void destroy() {
        destroyUp(head.next, false);
    }

    private void destroyUp(AbstractChannelHandlerContext ctx, boolean inEventLoop) {
        final Thread currentThread = Thread.currentThread();
        final AbstractChannelHandlerContext tail = this.tail;
        for (;;) {
            if (ctx == tail) {
                destroyDown(currentThread, tail.prev, inEventLoop);
                break;
            }
            final EventExecutor executor = ctx.executor();
            if (!inEventLoop && !executor.inEventLoop(currentThread)) {
                final AbstractChannelHandlerContext finalCtx = ctx;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        destroyUp(finalCtx, true);
                    }
                });
                break;
            }
            ctx = ctx.next;
            inEventLoop = false;
        }
    }

    private void destroyDown(Thread currentThread, AbstractChannelHandlerContext ctx, boolean inEventLoop) {
        final AbstractChannelHandlerContext head = this.head;
        for (;;) {
            if (ctx == head) {
                break;
            }
            final EventExecutor executor = ctx.executor();
            if (inEventLoop || executor.inEventLoop(currentThread)) {
                synchronized (this) {
                    remove0(ctx);
                }
                callHandlerRemoved0(ctx);
            } else {
                final AbstractChannelHandlerContext finalCtx = ctx;
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        destroyDown(Thread.currentThread(), finalCtx, true);
                    }
                });
                break;
            }

            ctx = ctx.prev;
            inEventLoop = false;
        }
    }

    @Override
    public final ChannelPipeline fireChannelActive() {
        AbstractChannelHandlerContext.invokeChannelActive(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelInactive() {
        AbstractChannelHandlerContext.invokeChannelInactive(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext.invokeExceptionCaught(head, cause);
        return this;
    }

    @Override
    public final ChannelPipeline fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext.invokeUserEventTriggered(head, event);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelRead(Object msg) {
        AbstractChannelHandlerContext.invokeChannelRead(head, msg);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelReadComplete() {
        AbstractChannelHandlerContext.invokeChannelReadComplete(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext.invokeChannelWritabilityChanged(head);
        return this;
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public final ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public final ChannelFuture close() {
        return tail.close();
    }

    @Override
    public final ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public final ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    /**
     * @Author: PP-jessica
     * @Description:可以看到，bind方法在ChannelPipeline中，是从尾节点开始发起的，然后向前面节点一次调用相同方法
     */
    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public final ChannelFuture connect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public final ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }

    @Override
    public final ChannelPipeline read() {
        tail.read();
        return this;
    }

    @Override
    public final ChannelFuture write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public final ChannelFuture write(Object msg, ChannelPromise promise) {
        return tail.write(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return tail.writeAndFlush(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }

    @Override
    public final ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel);
    }


    @Override
    public final ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        //return new FailedChannelFuture(channel, null, cause);
        return null;
    }



    /**
     * @Author: PP-jessica
     * @Description:检查名称是否重复了
     */
    private void checkDuplicateName(String name) {
        if (context0(name) != null) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    private AbstractChannelHandlerContext context0(String name) {
        AbstractChannelHandlerContext context = head.next;
        while (context != tail) {
            if (context.name().equals(name)) {
                return context;
            }
            context = context.next;
        }
        return null;
    }

    private AbstractChannelHandlerContext getContextOrDie(String name) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private AbstractChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private AbstractChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(handlerType);
        if (ctx == null) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    private void callHandlerAddedForAllHandlers() {
        //回调任务链表的头节点
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered;
            registered = true;
            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            //帮助垃圾回收
            this.pendingHandlerCallbackHead = null;
        }
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        // 挨个执行任务列表中的任务
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }

    private void callHandlerCallbackLater(AbstractChannelHandlerContext ctx, boolean added) {
        assert !registered;
        //如果是添加节点就创建PendingHandlerAddedTask对象，删除节点就创建PendingHandlerRemovedTask节点
        PendingHandlerCallback task = added ? new PendingHandlerAddedTask(ctx) : new PendingHandlerRemovedTask(ctx);
        PendingHandlerCallback pending = pendingHandlerCallbackHead;
        //如果链表还没有头节点，就把创建的对象设成头节点
        if (pending == null) {
            pendingHandlerCallbackHead = task;
        } else {
            //如果有头节点了，就把节点依次向后添加
            while (pending.next != null) {
                pending = pending.next;
            }
            pending.next = task;
        }
    }

    private void callHandlerAddedInEventLoop(final AbstractChannelHandlerContext newCtx, EventExecutor executor) {
        newCtx.setAddPending();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callHandlerAdded0(newCtx);
            }
        });
    }

    protected void onUnhandledInboundException(Throwable cause) {
        try {
            logger.warn(
                    "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                            "It usually means the last handler in the pipeline did not handle the exception.",
                    cause);
        } finally {
            //ReferenceCountUtil.release(cause);
        }
    }

    protected void onUnhandledInboundChannelActive() {
    }


    protected void onUnhandledInboundChannelInactive() {
    }


    protected void onUnhandledInboundMessage(Object msg) {
        try {
            logger.debug(
                    "Discarded inbound message {} that reached at the tail of the pipeline. " +
                            "Please check your pipeline configuration.", msg);
        } finally {
            //当该引用计数减至为0时，该ByteBuf即可回收，我们还未讲到这里，所以我先注释掉这个方法
            ReferenceCountUtil.release(msg);
        }
    }

    protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
        onUnhandledInboundMessage(msg);
        if (logger.isDebugEnabled()) {
            logger.debug("Discarded message pipeline : {}. Channel : {}.",
                    ctx.pipeline().names(), ctx.channel());
        }
    }


    protected void onUnhandledInboundChannelReadComplete() {
    }


    protected void onUnhandledInboundUserEventTriggered(Object evt) {
        // This may not be a configuration error and so don't log anything.
        // The event may be superfluous for the current pipeline configuration.
        //ReferenceCountUtil.release(evt);
    }


    protected void onUnhandledChannelWritabilityChanged() {
    }

//    @UnstableApi
//    protected void incrementPendingOutboundBytes(long size) {
//        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
//        if (buffer != null) {
//            buffer.incrementPendingOutboundBytes(size);
//        }
//    }

//    @UnstableApi
//    protected void decrementPendingOutboundBytes(long size) {
//        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
//        if (buffer != null) {
//            buffer.decrementPendingOutboundBytes(size);
//        }
//    }

    /**
     * @Author: PP-jessica
     * @Description:可以看出尾节点是个入站处理器
     */
    final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {

        TailContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, TAIL_NAME, TailContext.class);
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) { }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) { }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelInactive();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            onUnhandledChannelWritabilityChanged();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) { }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) { }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            onUnhandledInboundUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            onUnhandledInboundException(cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            //如果接收到的msg传到了尾节点，说明该数据没有被处理过，这里直接释放内存即可
            onUnhandledInboundMessage(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelReadComplete();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:头节点即是出站处理器，又是入站处理器
     */
    final class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler {

        private final Channel.Unsafe unsafe;

        HeadContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, HEAD_NAME, HeadContext.class);
            unsafe = pipeline.channel().unsafe();
            //设置channelHandler的状态为ADD_COMPLETE，说明该节点添加之后直接就可以处理数据
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {}

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {}

        @Override
        public void bind(
                ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
            //调用unsafe的方法，然后就是老样子了，再一路调用到NioServerSocketChannel中
            unsafe.bind(localAddress, promise);
        }

        @Override
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.close(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.deregister(promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            unsafe.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            unsafe.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            invokeHandlerAddedIfNeeded();
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
            if (!channel.isOpen()) {
                destroy();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            //在这个方法中给channel绑定读事件，跟随者调用链点到这里，应该能明白，不管是客户端还是服务端的channel，它们
            //绑定读事件，都是在channelActive被毁掉的过程中执行的
            readIfIsAutoRead();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.fireChannelReadComplete();

            readIfIsAutoRead();
        }

        /**
         * @Author: PP-jessica
         * @Description:在这个方法中给channel绑定读事件
         */
        private void readIfIsAutoRead() {
            if (channel.config().isAutoRead()) {
                channel.read();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            ctx.fireChannelWritabilityChanged();
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:下面这个抽象内部类和抽象内部类的两个实现子类就是构成回调链表的节点，看一看就行，逻辑十分简单
     */
    private abstract static class PendingHandlerCallback implements Runnable {
        final AbstractChannelHandlerContext ctx;
        PendingHandlerCallback next;

        PendingHandlerCallback(AbstractChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        abstract void execute();
    }

    private final class PendingHandlerAddedTask extends PendingHandlerCallback {

        PendingHandlerAddedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerAdded0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop(Thread.currentThread())) {
                callHandlerAdded0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerAdded() as the EventExecutor {} rejected it, removing handler {}.",
                                executor, ctx.name(), e);
                    }
                    remove0(ctx);
                    ctx.setRemoved();
                }
            }
        }
    }

    private final class PendingHandlerRemovedTask extends PendingHandlerCallback {

        PendingHandlerRemovedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerRemoved0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop(Thread.currentThread())) {
                callHandlerRemoved0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Can't invoke handlerRemoved() as the EventExecutor {} rejected it," +
                                        " removing handler {}.", executor, ctx.name(), e);
                    }
                    ctx.setRemoved();
                }
            }
        }
    }
}
