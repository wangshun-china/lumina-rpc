package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import com.lumina.rpc.core.exception.CircuitBreakerException;
import com.lumina.rpc.core.exception.RateLimitException;
import com.lumina.rpc.core.protection.ProtectionConfig;
import com.lumina.rpc.core.protection.ProtectionConfigClient;
import com.lumina.rpc.core.stats.RequestStatsReporter;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Failover 集群容错策略
 *
 * 失败自动重试其他服务器（Dubbo 默认策略）
 *
 * 特点：
 * - 当调用失败时，自动切换到其他服务器重试
 * - 适合读操作，不适合写操作（可能产生副作用）
 * - 通过 retries 参数控制重试次数
 * - 集成熔断器和限流器保护（支持动态配置）
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class FailoverCluster implements Cluster {

    private static final Logger logger = LoggerFactory.getLogger(FailoverCluster.class);

    public static final String NAME = "failover";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Object invoke(ClusterInvocation invocation) throws Throwable {
        String serviceName = invocation.getServiceName();
        String traceId = TraceContext.getTraceId();
        long startTime = System.currentTimeMillis();

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
                logger.warn("[Trace:{}] [Failover] Rate limit exceeded for: {} (limit: {}/s)",
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
                logger.warn("[Trace:{}] [Failover] Circuit breaker is OPEN for: {}", traceId, serviceName);
                throw new CircuitBreakerException(serviceName);
            }
        }

        // ========== 3. 执行调用（带重试） ==========
        int retries = invocation.getRetries();
        int maxAttempts = retries + 1;

        List<InetSocketAddress> failedAddresses = new ArrayList<>();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            InetSocketAddress address = invocation.selectAddress(failedAddresses);

            if (address == null) {
                logger.warn("[Trace:{}] [Failover] No more available servers for {}, failed addresses: {}",
                        traceId, serviceName, failedAddresses.size());
                break;
            }

            try {
                logger.debug("[Trace:{}] [Failover] Attempt {}/{} invoking {} at {}",
                        traceId, attempt, maxAttempts, serviceName, address);

                RpcResponse response = RpcInvoker.invoke(
                        address,
                        invocation.getRequest(),
                        invocation.getSerializer(),
                        invocation.getNettyClient(),
                        invocation.getTimeout()
                );

                if (response.isSuccess()) {
                    // ========== 4. 成功：记录成功 ==========
                    if (circuitBreaker != null) {
                        circuitBreaker.recordSuccess();
                    }

                    // 记录成功统计
                    long latency = System.currentTimeMillis() - startTime;
                    recordStats(serviceName, true, latency);

                    if (attempt > 1) {
                        logger.info("[Trace:{}] [Failover] Success on attempt {} for {}", traceId, attempt, serviceName);
                    }
                    return response.getData();
                } else {
                    throw new RuntimeException(response.getMessage());
                }

            } catch (Throwable e) {
                lastException = e;
                failedAddresses.add(address);

                logger.warn("[Trace:{}] [Failover] Attempt {}/{} failed for {} at {}: {}",
                        traceId, attempt, maxAttempts, serviceName, address, e.getMessage());

                if (attempt < maxAttempts) {
                    continue;
                }
            }
        }

        // ========== 5. 失败：记录失败 ==========
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure();
        }

        // 记录失败统计
        long latency = System.currentTimeMillis() - startTime;
        recordStats(serviceName, false, latency);

        logger.error("[Trace:{}] [Failover] All {} attempts failed for {}", traceId, maxAttempts, serviceName);
        throw lastException != null ? lastException :
                new RuntimeException("No available server for: " + serviceName);
    }

    /**
     * 获取动态保护配置
     */
    private ProtectionConfig getProtectionConfig(String serviceName) {
        try {
            return ProtectionConfigClient.getInstance().getConfig(serviceName);
        } catch (Exception e) {
            logger.debug("Failed to get protection config for {}, using defaults", serviceName);
            return null;
        }
    }

    /**
     * 记录请求统计
     */
    private void recordStats(String serviceName, boolean success, long latencyMs) {
        try {
            RequestStatsReporter.getInstance().recordRequest(serviceName, success, latencyMs);
        } catch (Exception e) {
            logger.debug("Failed to record stats for {}", serviceName);
        }
    }
}