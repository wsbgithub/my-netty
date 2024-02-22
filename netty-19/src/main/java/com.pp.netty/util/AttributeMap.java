package com.pp.netty.util;


/**
 * @Author: PP-jessica
 * @Description:netty自定义的map接口，该接口的实现类是一个map，
 */
public interface AttributeMap {

    <T> Attribute<T> attr(AttributeKey<T> key);

    <T> boolean hasAttr(AttributeKey<T> key);
}
