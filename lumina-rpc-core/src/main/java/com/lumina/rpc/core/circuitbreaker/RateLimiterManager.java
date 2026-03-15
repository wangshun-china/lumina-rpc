package com.lumina.rpc.core.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流器管理器
 *
 * 管理每个服务的限流器实例
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class RateLimiterManager {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterManager.class);

    /** 单例实例 */
    private static volatile RateLimiterManager instance;

    /** 服务限流器映射 */
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /** 默认每秒许可数 */
    private int defaultPermitsPerSecond = 100;

    private RateLimiterManager() {
    }

    /**
     * 获取单例实例
     */
    public static RateLimiterManager getInstance() {
        if (instance == null) {
            synchronized (RateLimiterManager.class) {
                if (instance == null) {
                    instance = new RateLimiterManager();
                }
            }
        }
        return instance;
    }

    /**
     * 获取或创建限流器
     *
     * @param serviceName 服务名称
     * @return 限流器
     */
    public RateLimiter getRateLimiter(String serviceName) {
        return rateLimiters.computeIfAbsent(serviceName,
                name -> new RateLimiter(name, defaultPermitsPerSecond));
    }

    /**
     * 获取或创建限流器（自定义阈值）
     *
     * @param serviceName 服务名称
     * @param permitsPerSecond 每秒许可数
     * @return 限流器
     */
    public RateLimiter getRateLimiter(String serviceName, int permitsPerSecond) {
        return rateLimiters.computeIfAbsent(serviceName,
                name -> new RateLimiter(name, permitsPerSecond));
    }

    /**
     * 尝试获取许可
     *
     * @param serviceName 服务名称
     * @return true 表示允许，false 表示被限流
     */
    public boolean tryAcquire(String serviceName) {
        RateLimiter limiter = getRateLimiter(serviceName);
        return limiter.tryAcquire();
    }

    /**
     * 尝试获取许可（自定义阈值）
     *
     * @param serviceName 服务名称
     * @param permitsPerSecond 每秒许可数
     * @return true 表示允许，false 表示被限流
     */
    public boolean tryAcquire(String serviceName, int permitsPerSecond) {
        RateLimiter limiter = rateLimiters.get(serviceName);

        // 如果限流器不存在或阈值不匹配，需要重建
        if (limiter == null || limiter.getPermitsPerSecond() != permitsPerSecond) {
            synchronized (this) {
                limiter = rateLimiters.get(serviceName);
                if (limiter == null || limiter.getPermitsPerSecond() != permitsPerSecond) {
                    limiter = new RateLimiter(serviceName, permitsPerSecond);
                    rateLimiters.put(serviceName, limiter);
                    logger.info("RateLimiter created/updated for {}: {} permits/s", serviceName, permitsPerSecond);
                }
            }
        }

        return limiter.tryAcquire();
    }

    /**
     * 动态更新限流阈值
     *
     * @param serviceName 服务名称
     * @param permitsPerSecond 新的每秒许可数
     */
    public void updatePermits(String serviceName, int permitsPerSecond) {
        // 移除旧的，创建新的
        rateLimiters.remove(serviceName);
        rateLimiters.put(serviceName, new RateLimiter(serviceName, permitsPerSecond));
        logger.info("RateLimiter updated for service: {} (new permits={}/s)", serviceName, permitsPerSecond);
    }

    /**
     * 设置默认许可数
     */
    public void setDefaultPermitsPerSecond(int permits) {
        this.defaultPermitsPerSecond = permits;
        logger.info("RateLimiter default permits updated: {}/s", permits);
    }

    /**
     * 获取所有限流器状态
     */
    public String getAllStats() {
        StringBuilder sb = new StringBuilder("RateLimiter Stats:\n");
        rateLimiters.forEach((name, limiter) -> {
            sb.append("  ").append(limiter.getStats()).append("\n");
        });
        return sb.toString();
    }

    /**
     * 重置指定服务的限流器
     */
    public void reset(String serviceName) {
        RateLimiter limiter = rateLimiters.get(serviceName);
        if (limiter != null) {
            limiter.reset();
            logger.info("RateLimiter reset for service: {}", serviceName);
        }
    }

    /**
     * 重置所有限流器
     */
    public void resetAll() {
        rateLimiters.values().forEach(RateLimiter::reset);
        logger.info("All RateLimiters reset");
    }

    /**
     * 获取所有限流器
     */
    public ConcurrentHashMap<String, RateLimiter> getAllRateLimiters() {
        return rateLimiters;
    }

    /**
     * 获取服务的通过数
     */
    public long getPassedCount(String serviceName) {
        RateLimiter limiter = rateLimiters.get(serviceName);
        return limiter != null ? limiter.getPassedCount() : 0;
    }

    /**
     * 获取服务的拒绝数
     */
    public long getRejectedCount(String serviceName) {
        RateLimiter limiter = rateLimiters.get(serviceName);
        return limiter != null ? limiter.getRejectedCount() : 0;
    }
}