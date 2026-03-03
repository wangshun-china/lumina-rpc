package com.lumina.rpc.protocol.common;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求ID生成器
 *
 * 使用原子长整型生成唯一的请求ID
 */
public class RequestIdGenerator {

    // 原子计数器
    private static final AtomicLong counter = new AtomicLong(0);

    // 单例实例
    private static final RequestIdGenerator INSTANCE = new RequestIdGenerator();

    private RequestIdGenerator() {
    }

    /**
     * 获取单例实例
     *
     * @return RequestIdGenerator 实例
     */
    public static RequestIdGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * 生成下一个请求ID
     *
     * @return 唯一的请求ID
     */
    public long nextId() {
        return counter.incrementAndGet();
    }

    /**
     * 获取当前计数器值（用于测试）
     *
     * @return 当前计数
     */
    public long currentCount() {
        return counter.get();
    }
}