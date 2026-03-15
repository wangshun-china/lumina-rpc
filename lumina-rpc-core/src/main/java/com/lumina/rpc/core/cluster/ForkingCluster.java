package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import com.lumina.rpc.core.exception.CircuitBreakerException;
import com.lumina.rpc.core.exception.RateLimitException;
import com.lumina.rpc.core.protection.ProtectionConfig;
import com.lumina.rpc.core.protection.ProtectionConfigClient;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forking 集群容错策略
 *
 * 并行调用多个服务器，一个成功即返回
 *
 * 特点：
 * - 同时向多个服务器发起调用
 * - 任一服务器返回成功即返回结果
 * - 适合实时性要求高的读操作
 * - 消耗更多服务器资源
 * - 集成熔断器和限流器保护
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class ForkingCluster implements Cluster {

    private static final Logger logger = LoggerFactory.getLogger(ForkingCluster.class);

    public static final String NAME = "forking";

    /** 默认并行调用数 */
    private static final int DEFAULT_FORKS = 2;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object invoke(ClusterInvocation invocation) throws Throwable {
        String serviceName = invocation.getServiceName();
        String traceId = TraceContext.getTraceId();

        // ========== 获取动态配置 ==========
        ProtectionConfig config = getProtectionConfig(serviceName);
        boolean enableCircuitBreaker = config != null ? config.isCircuitBreakerEnabled() : invocation.isEnableCircuitBreaker();
        boolean enableRateLimit = config != null ? config.isRateLimiterEnabled() : invocation.isEnableRateLimit();
        int circuitBreakerThreshold = config != null ? config.getCircuitBreakerThreshold() : invocation.getCircuitBreakerThreshold();
        long circuitBreakerTimeout = config != null ? config.getCircuitBreakerTimeout() : invocation.getCircuitBreakerTimeout();
        int rateLimitPermits = config != null ? config.getRateLimiterPermits() : invocation.getRateLimitPermits();

        // ========== 1. 限流检查 ==========
        if (enableRateLimit) {
            RateLimiterManager limiterManager = RateLimiterManager.getInstance();
            if (!limiterManager.tryAcquire(serviceName, rateLimitPermits)) {
                logger.warn("[Trace:{}] [Forking] Rate limit exceeded for: {} (limit: {}/s)",
                        traceId, serviceName, rateLimitPermits);
                throw new RateLimitException(serviceName, rateLimitPermits);
            }
        }

        // ========== 2. 熔断检查 ==========
        CircuitBreaker circuitBreaker = null;
        if (enableCircuitBreaker) {
            CircuitBreakerManager cbManager = CircuitBreakerManager.getInstance();
            circuitBreaker = cbManager.getCircuitBreaker(serviceName, 100,
                    circuitBreakerThreshold, circuitBreakerTimeout, 5);

            if (!circuitBreaker.allowRequest()) {
                logger.warn("[Trace:{}] [Forking] Circuit breaker is OPEN for: {}", traceId, serviceName);
                throw new CircuitBreakerException(serviceName);
            }
        }

        // ========== 3. 执行并行调用 ==========
        List<InetSocketAddress> addresses = invocation.getAllAddresses();

        if (addresses == null || addresses.isEmpty()) {
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            throw new RuntimeException("No available server for: " + serviceName);
        }

        int forks = Math.min(DEFAULT_FORKS, addresses.size());

        logger.debug("[Trace:{}] [Forking] Invoking {} with {} parallel calls", traceId, serviceName, forks);

        // 用于存储结果
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicBoolean recorded = new AtomicBoolean(false);
        final CircuitBreaker finalCircuitBreaker = circuitBreaker;
        final String finalTraceId = traceId;

        // 并行发起调用
        for (int i = 0; i < forks; i++) {
            InetSocketAddress address = addresses.get(i);

            CompletableFuture.runAsync(() -> {
                try {
                    RpcResponse response = RpcInvoker.invoke(
                            address,
                            invocation.getRequest(),
                            invocation.getSerializer(),
                            invocation.getNettyClient(),
                            invocation.getTimeout()
                    );

                    if (response.isSuccess()) {
                        // 第一个成功的，设置结果并记录成功
                        if (successCount.incrementAndGet() == 1) {
                            resultFuture.complete(response.getData());
                            logger.info("[Trace:{}] [Forking] First success for {} from {}",
                                    finalTraceId, serviceName, address);

                            // 记录成功（只记录一次）
                            if (finalCircuitBreaker != null && recorded.compareAndSet(false, true)) {
                                finalCircuitBreaker.recordSuccess();
                            }
                        }
                    } else {
                        handleFailure(failCount, forks, resultFuture,
                                "RPC failed: " + response.getMessage(), serviceName, finalTraceId);
                    }

                } catch (Throwable e) {
                    logger.warn("[Trace:{}] [Forking] Call failed for {} at {}: {}",
                            finalTraceId, serviceName, address, e.getMessage());
                    handleFailure(failCount, forks, resultFuture, e.getMessage(), serviceName, finalTraceId);
                }
            });
        }

        // 等待结果
        try {
            return resultFuture.get(invocation.getTimeout(), TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            // 记录失败
            if (finalCircuitBreaker != null && recorded.compareAndSet(false, true)) {
                finalCircuitBreaker.recordFailure();
            }

            logger.error("[Trace:{}] [Forking] All {} parallel calls failed for {}", traceId, forks, serviceName);
            throw new RuntimeException("All forked calls failed for: " + serviceName, e);
        }
    }

    private void handleFailure(AtomicInteger failCount, int total,
                               CompletableFuture<Object> resultFuture, String message, String serviceName, String traceId) {
        if (failCount.incrementAndGet() == total) {
            resultFuture.completeExceptionally(new RuntimeException("All calls failed: " + message));
        }
    }

    private ProtectionConfig getProtectionConfig(String serviceName) {
        try {
            return ProtectionConfigClient.getInstance().getConfig(serviceName);
        } catch (Exception e) {
            return null;
        }
    }
}