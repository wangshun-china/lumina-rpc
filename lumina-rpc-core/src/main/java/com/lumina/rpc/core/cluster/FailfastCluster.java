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

/**
 * Failfast 集群容错策略
 *
 * 快速失败，只发起一次调用，失败立即报错
 * 集成熔断器和限流器保护（支持动态配置）
 */
public class FailfastCluster implements Cluster {

    private static final Logger logger = LoggerFactory.getLogger(FailfastCluster.class);

    public static final String NAME = "failfast";

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
                logger.warn("[Trace:{}] [Failfast] Rate limit exceeded for: {} (limit: {}/s)",
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
                logger.warn("[Trace:{}] [Failfast] Circuit breaker is OPEN for: {}", traceId, serviceName);
                throw new CircuitBreakerException(serviceName);
            }
        }

        // ========== 3. 选择地址并调用 ==========
        InetSocketAddress address = invocation.selectAddress(null);

        if (address == null) {
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            throw new RuntimeException("No available server for: " + serviceName);
        }

        logger.debug("[Trace:{}] [Failfast] Invoking {} at {}", traceId, serviceName, address);

        try {
            RpcResponse response = RpcInvoker.invoke(
                    address,
                    invocation.getRequest(),
                    invocation.getSerializer(),
                    invocation.getNettyClient(),
                    invocation.getTimeout()
            );

            if (response.isSuccess()) {
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
                return response.getData();
            } else {
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                throw new RuntimeException("RPC call failed: " + response.getMessage());
            }

        } catch (Throwable e) {
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            logger.error("[Trace:{}] [Failfast] Fast fail for {} at {}: {}",
                    traceId, serviceName, address, e.getMessage());
            throw e;
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