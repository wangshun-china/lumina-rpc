package com.lumina.rpc.core.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器实现
 *
 * 状态机：CLOSED -> OPEN -> HALF_OPEN -> CLOSED/OPEN
 *
 * 核心特性：
 * 1. 滑动窗口统计错误率
 * 2. 三种状态：关闭（正常）、打开（熔断）、半开（试探）
 * 3. 可配置的阈值和恢复时间
 *
 * @author Lumina-RPC Team
 * @since 1.1.0
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    /**
     * 熔断器状态
     */
    public enum State {
        /** 关闭状态 - 正常调用 */
        CLOSED,
        /** 打开状态 - 熔断中，拒绝所有请求 */
        OPEN,
        /** 半开状态 - 允许部分请求通过，探测服务是否恢复 */
        HALF_OPEN
    }

    /** 服务名称 */
    private final String serviceName;

    /** 熔断器状态 */
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    /** 滑动窗口大小（请求数） */
    private final int windowSize;

    /** 错误率阈值（百分比） */
    private final int errorThreshold;

    /** 熔断持续时间（毫秒） */
    private final long openTimeout;

    /** 半开状态允许的探测请求数 */
    private final int halfOpenRequests;

    // ========== 滑动窗口统计 ==========

    /** 滑动窗口 - 成功计数 */
    private final AtomicInteger[] successWindow;

    /** 滑动窗口 - 失败计数 */
    private final AtomicInteger[] failureWindow;

    /** 当前窗口索引 */
    private final AtomicInteger currentWindowIndex = new AtomicInteger(0);

    /** 窗口总请求数 */
    private final AtomicInteger totalRequests = new AtomicInteger(0);

    /** 窗口错误数 */
    private final AtomicInteger totalErrors = new AtomicInteger(0);

    // ========== 时间控制 ==========

    /** 熔断开始时间 */
    private final AtomicLong openStartTime = new AtomicLong(0);

    /** 半开状态成功次数 */
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);

    /** 半开状态失败次数 */
    private final AtomicInteger halfOpenFailureCount = new AtomicInteger(0);

    /**
     * 创建熔断器
     *
     * @param serviceName     服务名称
     * @param windowSize      滑动窗口大小（请求数）
     * @param errorThreshold  错误率阈值（百分比，如 50 表示 50%）
     * @param openTimeout     熔断持续时间（毫秒）
     * @param halfOpenRequests 半开状态探测请求数
     */
    public CircuitBreaker(String serviceName, int windowSize, int errorThreshold,
                          long openTimeout, int halfOpenRequests) {
        this.serviceName = serviceName;
        this.windowSize = windowSize;
        this.errorThreshold = errorThreshold;
        this.openTimeout = openTimeout;
        this.halfOpenRequests = halfOpenRequests;

        // 初始化滑动窗口
        this.successWindow = new AtomicInteger[windowSize];
        this.failureWindow = new AtomicInteger[windowSize];
        for (int i = 0; i < windowSize; i++) {
            successWindow[i] = new AtomicInteger(0);
            failureWindow[i] = new AtomicInteger(0);
        }

        logger.info("CircuitBreaker created for service: {} (windowSize={}, errorThreshold={}%, openTimeout={}ms)",
                serviceName, windowSize, errorThreshold, openTimeout);
    }

    /**
     * 使用默认配置创建熔断器
     */
    public CircuitBreaker(String serviceName) {
        this(serviceName, 100, 50, 30000, 5);
    }

    /**
     * 检查是否允许请求通过
     *
     * @return true 表示允许，false 表示拒绝
     */
    public boolean allowRequest() {
        State currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                // 检查是否超过熔断时间
                if (System.currentTimeMillis() - openStartTime.get() >= openTimeout) {
                    // 尝试转换为半开状态
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenSuccessCount.set(0);
                        halfOpenFailureCount.set(0);
                        logger.info("CircuitBreaker [{}] transitioned to HALF_OPEN", serviceName);
                    }
                    return true;
                }
                return false;

            case HALF_OPEN:
                // 半开状态允许有限数量的请求通过
                int currentCount = halfOpenSuccessCount.get() + halfOpenFailureCount.get();
                return currentCount < halfOpenRequests;

            default:
                return true;
        }
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int successCount = halfOpenSuccessCount.incrementAndGet();

            // 半开状态下，成功次数达到阈值则关闭熔断器
            if (successCount >= halfOpenRequests) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    resetWindow();
                    logger.info("CircuitBreaker [{}] transitioned to CLOSED (service recovered)", serviceName);
                }
            }
        } else if (currentState == State.CLOSED) {
            recordToWindow(true);
            checkThreshold();
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int failureCount = halfOpenFailureCount.incrementAndGet();

            // 半开状态下，一次失败就回到打开状态
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openStartTime.set(System.currentTimeMillis());
                logger.warn("CircuitBreaker [{}] transitioned to OPEN (service still unhealthy)", serviceName);
            }
        } else if (currentState == State.CLOSED) {
            recordToWindow(false);
            checkThreshold();
        }
    }

    /**
     * 记录到滑动窗口
     */
    private void recordToWindow(boolean success) {
        int index = currentWindowIndex.getAndUpdate(i -> (i + 1) % windowSize);

        if (success) {
            successWindow[index].incrementAndGet();
            totalRequests.incrementAndGet();
        } else {
            failureWindow[index].incrementAndGet();
            totalRequests.incrementAndGet();
            totalErrors.incrementAndGet();
        }
    }

    /**
     * 检查错误率是否超过阈值
     */
    private void checkThreshold() {
        int requests = totalRequests.get();
        if (requests < windowSize / 2) {
            // 请求数太少，不做判断
            return;
        }

        int errors = totalErrors.get();
        double errorRate = (double) errors / requests * 100;

        if (errorRate >= errorThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                openStartTime.set(System.currentTimeMillis());
                logger.warn("CircuitBreaker [{}] OPENED - errorRate: {}/{} = {:.1f}% >= {}%",
                        serviceName, errors, requests, errorRate, errorThreshold);
            }
        }
    }

    /**
     * 重置滑动窗口
     */
    private void resetWindow() {
        totalRequests.set(0);
        totalErrors.set(0);
        for (int i = 0; i < windowSize; i++) {
            successWindow[i].set(0);
            failureWindow[i].set(0);
        }
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state.get();
    }

    /**
     * 获取服务名称
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 获取统计数据
     */
    public String getStats() {
        int requests = totalRequests.get();
        int errors = totalErrors.get();
        double errorRate = requests > 0 ? (double) errors / requests * 100 : 0;

        return String.format("CircuitBreaker[%s]: state=%s, requests=%d, errors=%d, errorRate=%.1f%%",
                serviceName, state.get(), requests, errors, errorRate);
    }

    @Override
    public String toString() {
        return getStats();
    }
}