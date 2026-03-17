package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import com.lumina.rpc.core.exception.CircuitBreakerException;
import com.lumina.rpc.core.exception.RateLimitException;
import com.lumina.rpc.core.protection.ProtectionConfig;
import com.lumina.rpc.core.protection.ProtectionConfigClient;
import com.lumina.rpc.core.spi.ActiveCounter;
import com.lumina.rpc.core.stats.RequestStatsReporter;
import com.lumina.rpc.core.trace.SpanCollector;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.trace.Span;
import com.lumina.rpc.protocol.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
 * - 支持同步和异步两种调用方式
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
        return doInvoke(invocation, false).join();
    }

    @Override
    public CompletableFuture<Object> invokeAsync(ClusterInvocation invocation) {
        return doInvoke(invocation, true);
    }

    /**
     * 核心调用逻辑（同步/异步统一实现）
     *
     * @param invocation 调用上下文
     * @param async 是否异步
     * @return CompletableFuture 包装的结果
     */
    private CompletableFuture<Object> doInvoke(ClusterInvocation invocation, boolean async) {
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        String serviceName = invocation.getServiceName();
        String methodName = invocation.getRequest().getMethodName();
        String traceId = TraceContext.getTraceId();
        long startTime = System.currentTimeMillis();

        // 创建 Span
        Span span = null;

        try {
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

            // 创建 Client Span
            span = SpanCollector.getInstance().startClientSpan(serviceName, methodName, null);
            span.addTag("retries", String.valueOf(retries));
            span.addTag("cluster", "failover");

            doInvokeWithRetry(invocation, async, 1, maxAttempts, new ArrayList<>(),
                    circuitBreaker, serviceName, traceId, startTime, resultFuture, span);

        } catch (Throwable e) {
            long latency = System.currentTimeMillis() - startTime;
            recordStats(serviceName, false, latency);
            if (span != null) {
                SpanCollector.getInstance().endSpanWithError(span, e.getMessage());
            }
            resultFuture.completeExceptionally(e);
        }

        return resultFuture;
    }

    /**
     * 递归重试调用
     */
    private void doInvokeWithRetry(ClusterInvocation invocation, boolean async,
                                    int attempt, int maxAttempts,
                                    List<InetSocketAddress> failedAddresses,
                                    CircuitBreaker circuitBreaker,
                                    String serviceName, String traceId,
                                    long startTime,
                                    CompletableFuture<Object> resultFuture,
                                    Span span) {

        InetSocketAddress address = invocation.selectAddress(failedAddresses);

        if (address == null) {
            logger.warn("[Trace:{}] [Failover] No more available servers for {}, failed addresses: {}",
                    traceId, serviceName, failedAddresses.size());

            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }

            long latency = System.currentTimeMillis() - startTime;
            recordStats(serviceName, false, latency);

            if (span != null) {
                SpanCollector.getInstance().endSpanWithError(span, "No available server");
            }

            resultFuture.completeExceptionally(
                    new RuntimeException("No available server for: " + serviceName));
            return;
        }

        // 更新 Span 的远程地址
        if (span != null) {
            span.setRemoteAddress(address.getHostString() + ":" + address.getPort());
        }

        // 记录活跃调用（用于最少活跃调用负载均衡）
        String addressKey = address.getHostString() + ":" + address.getPort();
        ActiveCounter.getInstance().increment(addressKey);

        logger.debug("[Trace:{}] [Failover] Attempt {}/{} invoking {} at {}",
                traceId, attempt, maxAttempts, serviceName, address);

        // 异步调用
        CompletableFuture<RpcResponse> responseFuture = RpcInvoker.invokeAsync(
                address,
                invocation.getRequest(),
                invocation.getNettyClient(),
                invocation.getTimeout()
        );

        final CircuitBreaker finalCircuitBreaker = circuitBreaker;
        final InetSocketAddress finalAddress = address;

        responseFuture.whenComplete((response, ex) -> {
            // 减少活跃调用计数
            ActiveCounter.getInstance().decrement(addressKey);

            if (ex != null) {
                // 调用失败
                logger.warn("[Trace:{}] [Failover] Attempt {}/{} failed for {} at {}: {}",
                        traceId, attempt, maxAttempts, serviceName, finalAddress, ex.getMessage());

                failedAddresses.add(finalAddress);

                if (attempt < maxAttempts) {
                    // 继续重试
                    doInvokeWithRetry(invocation, async, attempt + 1, maxAttempts,
                            failedAddresses, finalCircuitBreaker, serviceName, traceId,
                            startTime, resultFuture, span);
                } else {
                    // 重试次数用尽
                    if (finalCircuitBreaker != null) {
                        finalCircuitBreaker.recordFailure();
                    }

                    long latency = System.currentTimeMillis() - startTime;
                    recordStats(serviceName, false, latency);

                    if (span != null) {
                        SpanCollector.getInstance().endSpanWithError(span, ex.getMessage());
                    }

                    logger.error("[Trace:{}] [Failover] All {} attempts failed for {}", traceId, maxAttempts, serviceName);
                    resultFuture.completeExceptionally(ex);
                }

            } else if (response.isSuccess()) {
                // 调用成功
                if (finalCircuitBreaker != null) {
                    finalCircuitBreaker.recordSuccess();
                }

                long latency = System.currentTimeMillis() - startTime;
                recordStats(serviceName, true, latency);

                if (span != null) {
                    SpanCollector.getInstance().endSpan(span);
                }

                if (attempt > 1) {
                    logger.info("[Trace:{}] [Failover] Success on attempt {} for {}", traceId, attempt, serviceName);
                }
                resultFuture.complete(response.getData());

            } else {
                // 业务失败
                if (finalCircuitBreaker != null) {
                    finalCircuitBreaker.recordFailure();
                }

                long latency = System.currentTimeMillis() - startTime;
                recordStats(serviceName, false, latency);

                if (span != null) {
                    SpanCollector.getInstance().endSpanWithError(span, response.getMessage());
                }

                resultFuture.completeExceptionally(new RuntimeException(response.getMessage()));
            }
        });
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