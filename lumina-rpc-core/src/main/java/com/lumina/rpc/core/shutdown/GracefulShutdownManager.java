package com.lumina.rpc.core.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 优雅停机管理器
 *
 * 核心特性：
 * 1. 跟踪正在处理的请求数量 (in-flight requests)
 * 2. 停机时拒绝新请求
 * 3. 等待正在处理的请求完成
 * 4. 超时强制关闭
 *
 * 设计理念（对标 Dubbo）：
 * - 收到 SIGTERM/SIGINT 信号后，先从注册中心注销
 * - 标记为"停机中"，拒绝新请求
 * - 等待 in-flight 请求完成（固定超时 10 秒）
 * - 超时后强制关闭，打印未完成的请求日志
 * - 完全本地化设计，无需外部配置同步
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class GracefulShutdownManager {

    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdownManager.class);

    /** 单例实例 */
    private static volatile GracefulShutdownManager instance;

    /** 是否正在停机 */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** 正在处理的请求数量 */
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    /** 等待请求完成的最大时间（毫秒）- 本地固定配置 */
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000;

    /** 停机开始时间 */
    private volatile long shutdownStartTime;

    /** 停机回调 */
    private Runnable shutdownCallback;

    private GracefulShutdownManager() {
        // 注册 JVM 关闭钩子
        registerShutdownHook();
    }

    /**
     * 获取单例实例
     */
    public static GracefulShutdownManager getInstance() {
        if (instance == null) {
            synchronized (GracefulShutdownManager.class) {
                if (instance == null) {
                    instance = new GracefulShutdownManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册 JVM 关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("🛑 [ShutdownHook] JVM shutdown signal received");
            gracefulShutdown();
        }, "lumina-shutdown-hook"));
    }

    /**
     * 设置停机回调（如从注册中心注销）
     */
    public void setShutdownCallback(Runnable callback) {
        this.shutdownCallback = callback;
    }

    /**
     * 检查是否正在停机
     *
     * @return true 表示正在停机，应拒绝新请求
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * 请求开始时调用
     *
     * @return true 表示可以处理请求；false 表示正在停机，应拒绝请求
     */
    public boolean onRequestStart() {
        if (shuttingDown.get()) {
            logger.warn("[Graceful Shutdown] Rejecting new request - server is shutting down");
            return false;
        }

        activeRequests.incrementAndGet();
        return true;
    }

    /**
     * 请求结束时调用
     */
    public void onRequestEnd() {
        int remaining = activeRequests.decrementAndGet();

        // 如果正在停机且所有请求已完成，唤醒等待线程
        if (shuttingDown.get() && remaining == 0) {
            synchronized (this) {
                notifyAll();
            }
        }
    }

    /**
     * 获取正在处理的请求数量
     */
    public int getActiveRequestCount() {
        return activeRequests.get();
    }

    /**
     * 执行优雅停机
     */
    public void gracefulShutdown() {
        // 防止重复执行
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.info("[Graceful Shutdown] Already in progress");
            return;
        }

        shutdownStartTime = System.currentTimeMillis();
        logger.info("🛑 [Graceful Shutdown] Starting graceful shutdown...");

        // 1. 执行停机回调（如从注册中心注销）
        if (shutdownCallback != null) {
            try {
                logger.info("📡 [Graceful Shutdown] Executing shutdown callback (deregister from registry)...");
                shutdownCallback.run();
            } catch (Exception e) {
                logger.error("Error during shutdown callback", e);
            }
        }

        // 2. 等待正在处理的请求完成
        int activeCount = activeRequests.get();
        if (activeCount > 0) {
            logger.info("⏳ [Graceful Shutdown] Waiting for {} active requests to complete (timeout: {}ms)...",
                    activeCount, DEFAULT_SHUTDOWN_TIMEOUT_MS);

            long remainingTime = DEFAULT_SHUTDOWN_TIMEOUT_MS;
            while (activeRequests.get() > 0 && remainingTime > 0) {
                synchronized (this) {
                    try {
                        // 每次最多等待 100ms
                        long waitTime = Math.min(100, remainingTime);
                        wait(waitTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                remainingTime = DEFAULT_SHUTDOWN_TIMEOUT_MS - (System.currentTimeMillis() - shutdownStartTime);
            }

            // 检查是否还有未完成的请求
            int remaining = activeRequests.get();
            if (remaining > 0) {
                logger.warn("⚠️ [Graceful Shutdown] Timeout! Force closing with {} pending requests", remaining);
            } else {
                logger.info("✅ [Graceful Shutdown] All active requests completed");
            }
        } else {
            logger.info("✅ [Graceful Shutdown] No active requests to wait for");
        }

        long duration = System.currentTimeMillis() - shutdownStartTime;
        logger.info("🏁 [Graceful Shutdown] Completed in {}ms", duration);

        // 3. 停机完成后退出 JVM（模拟真实生产环境行为）
        // 注意：在测试环境中可能需要注释掉这行
        logger.info("💀 [Graceful Shutdown] Exiting JVM...");
        new Thread(() -> {
            try {
                Thread.sleep(500); // 给日志一点时间输出
                System.exit(0);
            } catch (InterruptedException e) {
                System.exit(0);
            }
        }, "jvm-exit-hook").start();
    }

    /**
     * 手动触发停机（用于测试）
     */
    public void shutdown() {
        gracefulShutdown();
    }

    /**
     * 重置状态（仅用于测试）
     */
    public void reset() {
        shuttingDown.set(false);
        activeRequests.set(0);
        shutdownStartTime = 0;
    }
}