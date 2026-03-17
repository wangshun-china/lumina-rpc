package com.lumina.rpc.core.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.common.RequestIdGenerator;
import com.lumina.rpc.protocol.trace.TraceContext;
import com.lumina.rpc.core.discovery.ServiceDiscovery;
import com.lumina.rpc.core.discovery.ServiceInstance;
import com.lumina.rpc.core.exception.NoProviderAvailableException;
import com.lumina.rpc.core.mock.MockRule;
import com.lumina.rpc.core.mock.MockRuleManager;
import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.spi.JsonSerializer;
import com.lumina.rpc.protocol.spi.SerializerManager;
import com.lumina.rpc.core.spi.LoadBalancer;
import com.lumina.rpc.core.spi.LoadBalancerManager;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.lumina.rpc.core.cluster.Cluster;
import com.lumina.rpc.core.cluster.ClusterInvocation;
import com.lumina.rpc.core.cluster.ClusterManager;
import com.lumina.rpc.core.protection.ProtectionConfig;

/**
 * RPC 客户端动态代理处理器
 *
 * 拦截被 @LuminaReference 标注的接口方法调用，封装为 RpcRequest 并发送
 * 完整流程：服务发现 -> 负载均衡 -> 连接池获取 Channel -> 发送请求
 *
 * 企业级 Mock 特性：
 * 1. 条件匹配：只有符合条件的调用才触发 Mock
 * 2. 双模引擎：SHORT_CIRCUIT（直接阻断）和 TAMPER（篡改真实数据）
 */
public class RpcClientHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    // 服务接口类
    private final Class<?> interfaceClass;

    // 服务版本号
    private final String version;

    // 超时时间（毫秒）
    private final long timeout;

    // 是否异步调用
    private final boolean async;

    // 集群策略
    private final String cluster;

    // 重试次数
    private final int retries;

    // 熔断器开关
    private final boolean enableCircuitBreaker;

    // 熔断器错误率阈值
    private final int circuitBreakerThreshold;

    // 熔断器恢复时间
    private final long circuitBreakerTimeout;

    // 限流器开关
    private final boolean enableRateLimit;

    // 限流阈值
    private final int rateLimitPermits;

    // ObjectMapper（用于类型转换兜底）
    private final ObjectMapper objectMapper;

    // Netty 客户端
    private final NettyClient nettyClient;

    // 负载均衡器
    private final LoadBalancer loadBalancer;

    // 请求ID生成器
    private final RequestIdGenerator requestIdGenerator;

    // 待处理请求管理器
    private final PendingRequestManager pendingRequestManager;

    // Mock 规则管理器（用于短路拦截）
    private final MockRuleManager mockRuleManager;

    public RpcClientHandler(Class<?> interfaceClass, String version, long timeout,
                            NettyClient nettyClient) {
        this(interfaceClass, version, timeout, false, "failover", 3, nettyClient,
                true, 50, 30000, false, 100);
    }

    public RpcClientHandler(Class<?> interfaceClass, String version, long timeout, boolean async,
                            NettyClient nettyClient) {
        this(interfaceClass, version, timeout, async, "failover", 3, nettyClient,
                true, 50, 30000, false, 100);
    }

    public RpcClientHandler(Class<?> interfaceClass, String version, long timeout, boolean async,
                            String cluster, int retries, NettyClient nettyClient) {
        this(interfaceClass, version, timeout, async, cluster, retries, nettyClient,
                true, 50, 30000, false, 100);
    }

    /**
     * 完整构造函数（包含熔断器和限流器配置）
     */
    public RpcClientHandler(Class<?> interfaceClass, String version, long timeout, boolean async,
                            String cluster, int retries, NettyClient nettyClient,
                            boolean enableCircuitBreaker, int circuitBreakerThreshold, long circuitBreakerTimeout,
                            boolean enableRateLimit, int rateLimitPermits) {
        this.interfaceClass = interfaceClass;
        this.version = version != null ? version : "";
        this.timeout = timeout > 0 ? timeout : 5000;
        this.async = async;
        this.cluster = cluster != null && !cluster.isEmpty() ? cluster : "failover";
        this.retries = retries > 0 ? retries : 3;
        this.enableCircuitBreaker = enableCircuitBreaker;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.circuitBreakerTimeout = circuitBreakerTimeout;
        this.enableRateLimit = enableRateLimit;
        this.rateLimitPermits = rateLimitPermits;
        this.nettyClient = nettyClient;
        this.loadBalancer = LoadBalancerManager.getDefaultLoadBalancer();
        this.requestIdGenerator = RequestIdGenerator.getInstance();
        this.pendingRequestManager = PendingRequestManager.getInstance();
        // 获取 ObjectMapper（用于类型转换兜底，从默认序列化器获取）
        var defaultSerializer = SerializerManager.getDefaultSerializer();
        this.objectMapper = (defaultSerializer instanceof JsonSerializer)
                ? ((JsonSerializer) defaultSerializer).getObjectMapper()
                : null;
        // 获取 Mock 规则管理器
        this.mockRuleManager = MockRuleManager.getInstance();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的方法（如 toString, hashCode, equals）
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String serviceName = interfaceClass.getName();
        String methodName = method.getName();

        // ========== 企业级 Mock 引擎：条件匹配 + 双模处理 ==========
        MockRule matchedRule = mockRuleManager.getMatchingRule(serviceName, methodName, args);

        if (matchedRule != null) {
            // 条件已匹配，根据 Mock 类型处理
            if (matchedRule.isShortCircuit()) {
                // 短路模式：直接返回 Mock 数据，不发起网络请求（传入 args 用于条件匹配）
                return mockRuleManager.executeMock(serviceName, methodName, args, method.getReturnType());
            } else if (matchedRule.isTamper()) {
                // 篡改模式：先发起真实调用，再合并 Mock 数据
                return invokeWithTamper(matchedRule, serviceName, methodName, method, args);
            }
        }

        // 构建 RpcRequest
        RpcRequest request = buildRpcRequest(method, args);

        // 异步调用支持
        if (async || isAsyncReturnType(method)) {
            return sendRequestAsync(request, method);
        }

        // 同步调用
        return sendRequest(request, method);
    }

    /**
     * 检查返回类型是否为 CompletableFuture
     */
    private boolean isAsyncReturnType(Method method) {
        return CompletableFuture.class.isAssignableFrom(method.getReturnType());
    }

    /**
     * 篡改模式调用：先真实调用，再合并 Mock 数据
     */
    private Object invokeWithTamper(MockRule rule, String serviceName, String methodName,
                                    Method method, Object[] args) throws Throwable {
        // 构建 RpcRequest
        RpcRequest request = buildRpcRequest(method, args);

        // 发起真实网络请求
        Object realResponse = sendRequest(request, method);

        // 执行数据篡改
        return mockRuleManager.executeTamper(rule, serviceName, methodName, realResponse, method.getReturnType());
    }

    /**
     * 构建 RPC 请求对象
     *
     * @param method 方法
     * @param args   参数
     * @return RpcRequest
     */
    private RpcRequest buildRpcRequest(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(requestIdGenerator.nextId());
        request.setInterfaceName(interfaceClass.getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args != null ? args : new Object[0]);
        request.setVersion(version);

        // 设置 Trace ID（如果当前上下文中没有则生成新的）
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = TraceContext.generateTraceId();
            TraceContext.setTraceId(traceId);
        }
        request.setTraceId(traceId);

        // 设置 MDC 以便日志自动包含 Trace ID
        MDC.put("traceId", traceId);

        logger.debug("[Trace:{}] Building request: {}.{}", traceId, interfaceClass.getSimpleName(), method.getName());
        return request;
    }

    /**
     * 异步发送 RPC 请求（走集群容错策略）
     *
     * @param request RPC 请求
     * @param method  调用的方法
     * @return CompletableFuture 包装的结果
     */
    private CompletableFuture<Object> sendRequestAsync(RpcRequest request, Method method) {
        String serviceName = request.getInterfaceName();

        try {
            // 服务发现
            List<ServiceInstance> instances = ServiceDiscovery.getServiceInstances(serviceName, version);
            if (instances.isEmpty()) {
                instances = ServiceDiscovery.getServiceInstances(serviceName);
            }
            if (instances.isEmpty()) {
                return CompletableFuture.failedFuture(new NoProviderAvailableException(serviceName));
            }

            // 获取动态配置
            ProtectionConfig config = getProtectionConfig(serviceName);
            long effectiveTimeout = config != null && config.getTimeout() > 0 ? config.getTimeout() : this.timeout;
            int effectiveRetries = config != null && config.getRetries() > 0 ? config.getRetries() : this.retries;
            String effectiveCluster = config != null && config.getClusterStrategy() != null ?
                    config.getClusterStrategy() : this.cluster;

            // 使用集群策略的异步调用
            Cluster clusterStrategy = ClusterManager.getInstance().getCluster(effectiveCluster);

            ClusterInvocation invocation = new ClusterInvocation(
                    serviceName, version, request, method.getReturnType(),
                    instances, loadBalancer, nettyClient, effectiveTimeout, effectiveRetries,
                    enableCircuitBreaker, circuitBreakerThreshold, circuitBreakerTimeout,
                    enableRateLimit, rateLimitPermits
            );

            logger.debug("[Async] Using cluster strategy: {} for service: {}", clusterStrategy.getName(), serviceName);

            // 调用集群策略的异步方法
            CompletableFuture<Object> resultFuture = clusterStrategy.invokeAsync(invocation);

            // 处理结果类型转换
            return resultFuture.thenApply(result -> convertResultType(result, method));

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 获取动态保护配置
     */
    private ProtectionConfig getProtectionConfig(String serviceName) {
        try {
            return com.lumina.rpc.core.protection.ProtectionConfigClient.getInstance().getConfig(serviceName);
        } catch (Exception e) {
            logger.debug("Failed to get protection config for {}, using defaults", serviceName);
            return null;
        }
    }

    /**
     * 发送 RPC 请求
     *
     * 完整流程：
     * 1. 服务发现 - 从本地缓存获取可用服务实例列表
     * 2. 动态配置 - 优先使用控制平面配置
     * 3. 集群容错 - 使用集群策略执行调用（Failover/Failfast/Failsafe/Forking）
     * 4. 结果转换 - 类型转换兜底处理
     *
     * @param request RPC 请求
     * @param method  调用的方法（用于返回类型转换）
     * @return 方法返回值
     * @throws Exception 调用异常
     */
    private Object sendRequest(RpcRequest request, Method method) throws Throwable {
        String serviceName = request.getInterfaceName();

        // ========== 步骤1: 服务发现 ==========
        List<ServiceInstance> instances = ServiceDiscovery.getServiceInstances(serviceName, version);

        if (instances.isEmpty()) {
            // 尝试获取所有版本的服务实例
            instances = ServiceDiscovery.getServiceInstances(serviceName);
            if (instances.isEmpty()) {
                logger.error("No available service provider for: {}", serviceName);
                throw new NoProviderAvailableException(serviceName);
            }
        }

        // ========== 步骤2: 获取动态配置 ==========
        ProtectionConfig config = getProtectionConfig(serviceName);
        long effectiveTimeout = config != null && config.getTimeout() > 0 ? config.getTimeout() : this.timeout;
        int effectiveRetries = config != null && config.getRetries() > 0 ? config.getRetries() : this.retries;
        String effectiveCluster = config != null && config.getClusterStrategy() != null ?
                config.getClusterStrategy() : this.cluster;

        logger.debug("Dynamic config for {}: timeout={}, retries={}, cluster={}",
                serviceName, effectiveTimeout, effectiveRetries, effectiveCluster);

        // ========== 步骤3: 集群容错调用 ==========
        Cluster clusterStrategy = ClusterManager.getInstance().getCluster(effectiveCluster);

        ClusterInvocation invocation = new ClusterInvocation(
                serviceName, version, request, method.getReturnType(),
                instances, loadBalancer, nettyClient, effectiveTimeout, effectiveRetries,
                enableCircuitBreaker, circuitBreakerThreshold, circuitBreakerTimeout,
                enableRateLimit, rateLimitPermits
        );

        logger.debug("Using cluster strategy: {} for service: {}", clusterStrategy.getName(), serviceName);

        Object result = clusterStrategy.invoke(invocation);

        // ========== 步骤4: 结果类型转换 ==========
        result = convertResultType(result, method);

        return result;
    }

    /**
     * 转换结果类型（兜底处理）
     *
     * 当 ObjectMapper 反序列化出的对象类型为 LinkedHashMap 时，
     * 尝试转换为方法声明的返回类型
     *
     * @param result 原始响应数据
     * @param method 调用的方法
     * @return 转换后的结果
     */
    private Object convertResultType(Object result, Method method) {
        if (result == null) {
            return null;
        }

        Class<?> returnType = method.getReturnType();

        // 如果类型已经匹配，直接返回
        if (returnType.isAssignableFrom(result.getClass())) {
            return result;
        }

        // 兜底转换：将 LinkedHashMap 转换为目标类型
        if (objectMapper != null && result instanceof java.util.Map) {
            try {
                Object converted = objectMapper.convertValue(result, returnType);
                logger.debug("Converted result from {} to {}", result.getClass().getName(), returnType.getName());
                return converted;
            } catch (Exception e) {
                logger.warn("Failed to convert result type: {} -> {}",
                        result.getClass().getName(), returnType.getName(), e);
                // 转换失败时返回原始结果，让调用方收到 ClassCastException
            }
        }

        return result;
    }
}
