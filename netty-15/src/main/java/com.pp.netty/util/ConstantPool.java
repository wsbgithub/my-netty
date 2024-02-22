package com.pp.netty.util;

import com.pp.netty.util.internal.ObjectUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @Author: PP-jessica
 * @Description:常量池，其实是个ConcurrentMap，以key-value的方式存储用户定义的配置信息
 */
public abstract class ConstantPool<T extends Constant<T>> {

    private final ConcurrentMap<String, T> constants = new ConcurrentHashMap<>();

    /**
     * @Author: PP-jessica
     * @Description:初始化常量类的id，初值为1
     */
    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * @Author: PP-jessica
     * @Description:创建常量类的方法
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        if (firstNameComponent == null) {
            throw new NullPointerException("firstNameComponent");
        }
        if (secondNameComponent == null) {
            throw new NullPointerException("secondNameComponent");
        }
        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法也是创建常量的方法
     */
    public T valueOf(String name) {
        checkNotNullAndNotEmpty(name);
        return getOrCreate(name);
    }

    /**
     * @Author: PP-jessica
     * @Description:真正创建常量类的方法，这里的参数就是常量的名字，创建的常量是以key-name，value-ChannelOption<T>的形式
     * 存储在map中的
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name);
        //先判断常量池中是否有该常量
        if (constant == null) {
            //没有的话就创建
            final T tempConstant = newConstant(nextId(), name);
            //然后放进常量池中
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                return tempConstant;
            }
        }
        //最后返回该常量
        return constant;
    }

    /**
     * @Author: PP-jessica
     * @Description:判断常量是否存在，也就是常量池中是否有常量的key
     */
    public boolean exists(String name) {
        checkNotNullAndNotEmpty(name);
        return constants.containsKey(name);
    }


    /**
     * @Author: PP-jessica
     * @Description:也是创建常量的方法，只不过这里调用了另一个创建方法
     */
    public T newInstance(String name) {
        checkNotNullAndNotEmpty(name);
        return createOrThrow(name);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法和getOrCreate并没什么区别，只不过该方法会在创建失败后抛出异常，表示该常量已经在使用了
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T tempConstant = newConstant(nextId(), name);
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                return tempConstant;
            }
        }
        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    private static String checkNotNullAndNotEmpty(String name) {
        ObjectUtil.checkNotNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        return name;
    }

    /**
     * @Author: PP-jessica
     * @Description:终于又看到一个抽象方法了，把该方法设置为抽象的，立刻就应该想到该抽象类有不止一个实现子类，ChannelOption只是其中之一
     */
    protected abstract T newConstant(int id, String name);

    /**
     * @Author: PP-jessica
     * @Description:该方法虽然被标注为废弃的了，但是仍然在被使用，所以我也没有删掉它
     */
    @Deprecated
    public final int nextId() {
        return nextId.getAndIncrement();
    }
}
