package com.lumina.rpc.core.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流器
 *
 * 基于令牌桶算法实现限流：
 * 1. 以固定速率向桶中添加令牌
 * 2. 请求到达时尝试从桶中获取令牌
 * 3. 获取成功则允许请求，失败则拒绝
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    /** 服务名称 */
    private final String serviceName;

    /** 每秒生成的令牌数（QPS 阈值） */
    private volatile int permitsPerSecond;

    /** 桶容量（最大令牌数） */
    private volatile int maxTokens;

    /** 当前令牌数（使用浮点数支持低 QPS） */
    private final java.util.concurrent.atomic.AtomicReference<Double> tokens;

    /** 上次补充令牌的时间（纳秒） */
    private final AtomicLong lastRefillTime;

    /** 被拒绝的请求数 */
    private final AtomicInteger rejectedCount = new AtomicInteger(0);

    /** 通过的请求数 */
    private final AtomicInteger passedCount = new AtomicInteger(0);

    /**
     * 创建限流器
     *
     * @param serviceName 服务名称
     * @param permitsPerSecond 每秒允许的请求数
     */
    public RateLimiter(String serviceName, int permitsPerSecond) {
        this.serviceName = serviceName;
        this.permitsPerSecond = permitsPerSecond;
        // 桶容量 = QPS * 5，允许5秒的突发流量（至少为 permitsPerSecond）
        this.maxTokens = Math.max(permitsPerSecond * 5, permitsPerSecond);
        this.tokens = new java.util.concurrent.atomic.AtomicReference<>((double) maxTokens);
        this.lastRefillTime = new AtomicLong(System.nanoTime());

        logger.info("RateLimiter created for service: {} (permits={}/s, maxTokens={})",
                serviceName, permitsPerSecond, maxTokens);
    }

    /**
     * 尝试获取令牌
     *
     * @return true 表示获取成功，false 表示被限流
     */
    public boolean tryAcquire() {
        // 先补充令牌
        refill();

        // 尝试获取令牌
        while (true) {
            Double currentTokens = tokens.get();
            if (currentTokens < 1.0) {
                rejectedCount.incrementAndGet();
                logger.debug("RateLimiter [{}] rejected request (tokens={})", serviceName, currentTokens);
                return false;
            }

            if (tokens.compareAndSet(currentTokens, currentTokens - 1.0)) {
                passedCount.incrementAndGet();
                return true;
            }
        }
    }

    /**
     * 补充令牌
     *
     * 根据时间差计算应该补充的令牌数（支持低 QPS，如 1/s）
     */
    private void refill() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTime.get();

        // 计算时间差（纳秒）
        long elapsedNanos = now - lastRefill;
        if (elapsedNanos < 1_000_000) { // 小于1ms不补充
            return;
        }

        // 计算应该补充的令牌数（使用纳秒精度，支持低 QPS）
        // permitsPerSecond / 1_000_000_000 = 每纳秒生成的令牌数
        double tokensToAdd = (double) elapsedNanos * permitsPerSecond / 1_000_000_000.0;

        if (tokensToAdd <= 0) {
            return;
        }

        // CAS 更新上次补充时间
        if (!lastRefillTime.compareAndSet(lastRefill, now)) {
            return; // 其他线程已经在补充
        }

        // 补充令牌，不超过最大值
        while (true) {
            Double currentTokens = tokens.get();
            double newTokens = Math.min(currentTokens + tokensToAdd, (double) maxTokens);

            if (tokens.compareAndSet(currentTokens, newTokens)) {
                break;
            }
        }
    }

    /**
     * 获取当前令牌数
     */
    public int getAvailableTokens() {
        return tokens.get().intValue();
    }

    /**
     * 获取服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取每秒许可数
     */
    public int getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 更新每秒许可数（重建限流器）
     */
    public void updatePermitsPerSecond(int newPermits) {
        this.permitsPerSecond = newPermits;
        this.maxTokens = Math.max(newPermits, 1);
        logger.info("RateLimiter [{}] updated: {} permits/s", serviceName, newPermits);
    }

    /**
     * 获取被拒绝数
     */
    public int getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * 获取通过数
     */
    public int getPassedCount() {
        return passedCount.get();
    }

    /**
     * 重置统计
     */
    public void reset() {
        tokens.set((double) maxTokens);
        rejectedCount.set(0);
        passedCount.set(0);
        lastRefillTime.set(System.nanoTime());
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("RateLimiter[%s]: permits=%d/s, tokens=%.2f, passed=%d, rejected=%d",
                serviceName, permitsPerSecond, tokens.get(), passedCount.get(), rejectedCount.get());
    }

    @Override
    public String toString() {
        return getStats();
    }
}