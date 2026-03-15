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

    /** 默认配置 */
    private int defaultWindowSize = 100;
    private int defaultErrorThreshold = 50;
    private long defaultOpenTimeout = 30000;
    private int defaultHalfOpenRequests = 5;

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
     * 获取或创建熔断器
     *
     * @param serviceName 服务名称
     * @return 熔断器
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName,
                name -> new CircuitBreaker(name, defaultWindowSize, defaultErrorThreshold,
                        defaultOpenTimeout, defaultHalfOpenRequests));
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
     * 检查服务是否可用
     *
     * @param serviceName 服务名称
     * @return true 表示可用，false 表示熔断中
     */
    public boolean isAvailable(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb == null) {
            return true;
        }
        return cb.allowRequest();
    }

    /**
     * 记录成功
     */
    public void recordSuccess(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.recordSuccess();
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        if (cb != null) {
            cb.recordFailure();
        }
    }

    /**
     * 设置默认配置
     */
    public void setDefaultConfig(int windowSize, int errorThreshold, long openTimeout, int halfOpenRequests) {
        this.defaultWindowSize = windowSize;
        this.defaultErrorThreshold = errorThreshold;
        this.defaultOpenTimeout = openTimeout;
        this.defaultHalfOpenRequests = halfOpenRequests;
        logger.info("CircuitBreaker default config updated: windowSize={}, errorThreshold={}%, openTimeout={}ms",
                windowSize, errorThreshold, openTimeout);
    }

    /**
     * 获取所有熔断器状态
     */
    public String getAllStats() {
        StringBuilder sb = new StringBuilder("CircuitBreaker Stats:\n");
        circuitBreakers.forEach((name, cb) -> {
            sb.append("  ").append(cb.getStats()).append("\n");
        });
        return sb.toString();
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

    /**
     * 获取服务的熔断器状态
     */
    public String getState(String serviceName) {
        CircuitBreaker cb = circuitBreakers.get(serviceName);
        return cb != null ? cb.getState().name() : "CLOSED";
    }
}