package com.lumina.rpc.core.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器管理器
 *
 * 管理每个服务的熔断器实例
 *
 * @author Lumina-RPC Team
 * @since 1.1.0
 */
public class CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);

    /** 单例实例 */
    private static volatile CircuitBreakerManager instance;

    /** 服务熔断器映射 */
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    private CircuitBreakerManager() {
    }

    /**
     * 获取单例实例
     */
    public static CircuitBreakerManager getInstance() {
        if (instance == null) {
            synchronized (CircuitBreakerManager.class) {
                if (instance == null) {
                    instance = new CircuitBreakerManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取或创建熔断器（自定义配置）
     *
     * @param serviceName 服务名称
     * @param windowSize 滑动窗口大小
     * @param errorThreshold 错误率阈值
     * @param openTimeout 熔断恢复时间
     * @param halfOpenRequests 半开状态探测请求数
     * @return 熔断器
     */
    public CircuitBreaker getCircuitBreaker(String serviceName, int windowSize, int errorThreshold,
                                            long openTimeout, int halfOpenRequests) {
        return circuitBreakers.computeIfAbsent(serviceName,
                name -> new CircuitBreaker(name, windowSize, errorThreshold, openTimeout, halfOpenRequests));
    }

    /**
     * 重置指定服务的熔断器
     */
    public void reset(String serviceName) {
        circuitBreakers.remove(serviceName);
        logger.info("CircuitBreaker reset for service: {}", serviceName);
    }

    /**
     * 重置所有熔断器
     */
    public void resetAll() {
        circuitBreakers.clear();
        logger.info("All CircuitBreakers reset");
    }

    /**
     * 获取所有熔断器
     */
    public ConcurrentHashMap<String, CircuitBreaker> getAllCircuitBreakers() {
        return circuitBreakers;
    }
}