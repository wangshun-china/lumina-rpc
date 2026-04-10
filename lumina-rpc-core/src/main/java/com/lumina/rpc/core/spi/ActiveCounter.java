package com.lumina.rpc.core.spi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 活跃调用计数器
 *
 * 用于最少活跃调用负载均衡策略，记录每个服务实例的活跃请求数
 */
public class ActiveCounter {

    private static final ActiveCounter INSTANCE = new ActiveCounter();

    // 存储每个服务实例的活跃调用数，key 格式：host:port
    private final ConcurrentHashMap<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    private ActiveCounter() {
    }

    public static ActiveCounter getInstance() {
        return INSTANCE;
    }

    /**
     * 增加活跃调用数
     *
     * @param address 服务地址 (host:port)
     */
    public void increment(String address) {
        AtomicInteger counter = activeCounts.computeIfAbsent(address, k -> new AtomicInteger(0));
        counter.incrementAndGet();
    }

    /**
     * 减少活跃调用数
     *
     * @param address 服务地址 (host:port)
     */
    public void decrement(String address) {
        AtomicInteger counter = activeCounts.get(address);
        if (counter != null) {
            int value = counter.decrementAndGet();
            if (value < 0) {
                counter.set(0);
            }
        }
    }

    /**
     * 获取活跃调用数
     *
     * @param address 服务地址 (host:port)
     * @return 活跃调用数
     */
    public int getActiveCount(String address) {
        AtomicInteger counter = activeCounts.get(address);
        return counter == null ? 0 : counter.get();
    }
}