package com.lumina.rpc.protocol.spi;

/**
 * 序列化器接口
 */
public interface Serializer {

    /**
     * 序列化对象为字节数组
     *
     * @param obj 要序列化的对象
     * @return 字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化字节数组为对象
     *
     * @param bytes 字节数组
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 获取序列化类型代码
     *
     * @return 序列化类型代码
     */
    byte getType();

    /**
     * 获取序列化器名称
     *
     * @return 名称
     */
    String getName();
}