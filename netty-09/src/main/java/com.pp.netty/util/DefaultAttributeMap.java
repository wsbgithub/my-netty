package com.pp.netty.util;


import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @Author: PP-jessica
 * @Description:AttributeMap的实现类，引入该类后，我们就会发现，AbstractChannel实际上继承了该实现类，并且Channel接口继承了
 * AttributeMap接口，这说明channel本身也是一个map。netty的作者为什么要搞这样一个map出来呢？好像很多时候我们用netty时根本用不到
 * attr(AttributeKey.valueOf(xxx),xx)这个方法。我本人倒是没怎么用到过，不过该方法可以在map中存储各种数据，
 * 并且只要是在该channel的处理过程中，比如在channelHandler中，就可以获得到这些用户定义的数据，算是一个非常方便的扩展点。
 */
public class DefaultAttributeMap implements AttributeMap{

    /**
     * @Author: PP-jessica
     * @Description:原子更新器，这个更新器更新的是map的value的值，在这里，原子更新器是为了解决map添加数据时的并发问题。在hashmap中
     * 哈希桶是普通的数组，而在这个map中，哈希桶为一个原子引用数组。
     */
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");

    //数组的初始大小为4
    private static final int BUCKET_SIZE = 4;
    //掩码为3，要做位运算求数组下标，这意味着该数组不必扩容
    private static final int MASK = BUCKET_SIZE  - 1;


    @SuppressWarnings("UnusedDeclaration")
    //哈希桶数组，并不在这里初始化。
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    @SuppressWarnings("unchecked")
    /**
     * @Author: PP-jessica
     * @Description:该方法的作用是添加一个数据，并且返回一个Attribute类型的对象
     * 该对象中封装着map的key和value
     */
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        //如果数组不等于null，说明已经初始化过，不是第一次向map中存数据了
        if (attributes == null) {
            //为null则初始化，数组的长度是固定的
            attributes = new AtomicReferenceArray<DefaultAttribute<?>>(BUCKET_SIZE);
            //用原子更新器把attributes更新成初始化好的数组，这里要注意一下，虽然上面数组已经初始化好了，但初始化好的数组赋值给了局部变量
            //到这里，才真正把初始化好的数组给到了对象中的attributes属性
            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }
        //计算数据在数组中存储的下标
        int i = index(key);
        //这里就可以类比向hashmap中添加数据的过程了，计算出下标后，先判断该下标上是否有数据
        DefaultAttribute<?> head = attributes.get(i);
        //为null则说明暂时没有数据，可以直接添加，否则就要以链表的形式添加。这里当然也不能忘记并发的情况，如果多个线程都洗向这个位置添加数据呢
        if (head == null) {
            //初始化一个头节点，但里面不存储任何数据
            head = new DefaultAttribute();
            //创建一个DefaultAttribute对象，把头节点和key作为参数传进去。实际上，这里创建的DefaultAttribute对象就是该map中存储的value对象
            //当然，这么说也不准确，确切地说，应该是要存储的value就存放在DefaultAttribute对象中，而DefaultAttribute对象存储在数组中
            DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
            //头节点是空节点，也是存放在数组的节点，头节点的下一个节点就是刚刚创建的attr对象
            head.next = attr;
            //att的前一个节点就是头节点
            attr.prev = head;
            //用cas给数组下标位置赋值，这里就应该想到并发问题，只要有一个线程原子添加成功就行
            if (attributes.compareAndSet(i, null, head)) {
                //返回创建的DefaultAttribute对象
                return attr;
            } else {
                //走着这里说明该线程设置头节点失败，这时候就要把头节点重新赋值，因为其他线程已经把头节点添加进去了，就要用添加进去的头节点赋值
                head = attributes.get(i);
            }
        }
        //走到这里说明头节点已经初始化过了，说明要添加的位置已经有值，需要以链表的方法继续添加数据
        synchronized (head) {
            //把当前节点赋值为头节点
            DefaultAttribute<?> curr = head;
            for (;;) {
                //得到当前节点的下一个节点
                DefaultAttribute<?> next = curr.next;
                //如果为null，说明当前节点就是最后一个节点
                if (next == null) {
                    //创建DefaultAttribute对象，封装数据
                    DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
                    //当前节点下一个节点为attr
                    curr.next = attr;
                    //attr的上一个节点为当前节点，从这里可以看出netty定义的map中链表采用的是尾插法
                    attr.prev = curr;
                    return attr;
                }
                //如果下一个节点和传入的key相等，并且该节点并没有被删除，说明map中已经存在该数据了，直接返回该数据即可
                if (next.key == key && !next.removed) {
                    return (Attribute<T>) next;
                }
                //把下一个节点赋值为当前节点
                curr = next;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:判断该map中是否存在该key，逻辑很简单，就不一一分析了
     */
    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            return false;
        }
        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            return false;
        }
        synchronized (head) {
            DefaultAttribute<?> curr = head.next;
            while (curr != null) {
                if (curr.key == key && !curr.removed) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }
    }

    private static int index(AttributeKey<?> key) {
        // 与掩码&运算，数值肯定<=mask 正好是数组下标
        return key.id() & MASK;
    }


    /**
     * @Author: PP-jessica
     * @Description:静态内部类，该类继承了AtomicReference，就是AtomicReference类，封装了map数据中的value
     * 很快我们就会在别的地方看到这样一行代码channel.attr(key).set(e.getValue());
     * 其中set方法，就是调用了AtomicReference类中的set方法，把要存储的value以cas的方式存储到AtomicReference类中
     */
    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        /**
         * @Author: PP-jessica
         * @Description:要封装的数据
         */
        private final DefaultAttribute<?> head;
        private final AttributeKey<T> key;

        /**
         * @Author: PP-jessica
         * @Description:前节点和后节点
         */
        private DefaultAttribute<?> prev;
        private DefaultAttribute<?> next;

        /**
         * @Author: PP-jessica
         * @Description:节点是否被删除了，volatile修饰的变量
         */
        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        DefaultAttribute() {
            head = this;
            key = null;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        @Override
        public T setIfAbsent(T value) {
            //原子引用类用cas把要存储的value存储到类中
            while (!compareAndSet(null, value)) {
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() {
            removed = true;
            T oldValue = getAndSet(null);
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            //表示节点已删除
            removed = true;
            //既然DefaultAttribute都删除了，那么DefaultAttribute中存储的value也该置为null了
            set(null);
            //删除一个节点，重排链表指针
            remove0();
        }

        private void remove0() {
            synchronized (head) {
                if (prev == null) {
                    return;
                }
                prev.next = next;
                if (next != null) {
                    next.prev = prev;
                }
                prev = null;
                next = null;
            }
        }
    }
}
