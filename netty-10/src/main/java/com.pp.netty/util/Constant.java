package com.pp.netty.util;

/**
 * @Author: PP-jessica
 * @Description:常量类的顶级接口，定义了常量的id和名字
 */
public interface Constant<T extends Constant<T>> extends Comparable<T> {

    int id();

    String name();
}
