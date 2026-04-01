package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import com.lumina.rpc.core.client.ControlPlaneClient;
import com.lumina.rpc.core.protection.ProtectionConfig;
import com.lumina.rpc.core.spi.SelectionResult;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;

/**
 * Failsafe 集群容错策略
 *
 * 失败安全，出现异常时直接忽略，返回 null 或默认值
 * 集成熔断器和限流器保护（支持动态配置）
 *
 * 改进：
 * - 负载均衡器负责节点选择和状态管理
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class FailsafeCluster implements Cluster {

    private static final Logger logger = LoggerFactory.getLogger(FailsafeCluster.class);

    public static final String NAME = "failsafe";

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

        // ========== 1. 限流检查（Failsafe 模式下被限流也返回 null） ==========
        if (enableRateLimit) {
            RateLimiterManager limiterManager = RateLimiterManager.getInstance();
            if (!limiterManager.tryAcquire(serviceName, rateLimitPermits)) {
                logger.warn("[Trace:{}] [Failsafe] Rate limit exceeded for: {}, returning null", traceId, serviceName);
                return null;
            }
        }

        // ========== 2. 熔断检查（Failsafe 模式下熔断也返回 null） ==========
        CircuitBreaker circuitBreaker = null;
        if (enableCircuitBreaker) {
            CircuitBreakerManager cbManager = CircuitBreakerManager.getInstance();
            circuitBreaker = cbManager.getCircuitBreaker(serviceName, 100,
                    circuitBreakerThreshold, circuitBreakerTimeout, 5);

            if (!circuitBreaker.allowRequest()) {
                logger.warn("[Trace:{}] [Failsafe] Circuit breaker is OPEN for: {}, returning null", traceId, serviceName);
                return null;
            }
        }

        // ========== 3. 选择节点（负载均衡器内部处理状态管理） ==========
        SelectionResult selection = invocation.getLoadBalancer().selectWithExclusion(
                invocation.getInstances(),
                Collections.emptyList(),
                serviceName,
                null
        );

        if (selection == null || selection.getAddress() == null) {
            logger.warn("[Trace:{}] [Failsafe] No available server for {}, returning null", traceId, serviceName);
            return null;
        }

        InetSocketAddress address = selection.getAddress();
        Runnable onCompleteCallback = selection.getOnComplete();

        logger.debug("[Trace:{}] [Failsafe] Invoking {} at {}", traceId, serviceName, address);

        try {
            RpcResponse response = RpcInvoker.invoke(
                    address,
                    invocation.getRequest(),
                    invocation.getNettyClient(),
                    invocation.getTimeout()
            );

            // 执行完成回调
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }

            if (response.isSuccess()) {
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
                return response.getData();
            } else {
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }
                logger.warn("[Trace:{}] [Failsafe] RPC call failed for {}: {}, returning null",
                        traceId, serviceName, response.getMessage());
                return null;
            }

        } catch (Throwable e) {
            // 执行完成回调（即使失败也要清理状态）
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }

            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            logger.warn("[Trace:{}] [Failsafe] Exception ignored for {} at {}: {}, returning null",
                    traceId, serviceName, address, e.getMessage());
            return null;
        }
    }

    private ProtectionConfig getProtectionConfig(String serviceName) {
        try {
            return ControlPlaneClient.getInstance().getProtectionConfig(serviceName);
        } catch (Exception e) {
            return null;
        }
    }
}