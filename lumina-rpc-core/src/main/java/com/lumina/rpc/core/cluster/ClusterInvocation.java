package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiter;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import com.lumina.rpc.core.discovery.ServiceInstance;
import com.lumina.rpc.core.exception.CircuitBreakerException;
import com.lumina.rpc.core.exception.RateLimitException;
import com.lumina.rpc.core.spi.LoadBalancer;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Supplier;

/**
 * 集群调用上下文
 *
 * 封装一次 RPC 调用所需的所有信息
 * 集成熔断器和限流器保护
 * 使用默认序列化器（KRYO）进行消息编码
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class ClusterInvocation {

    private static final Logger logger = LoggerFactory.getLogger(ClusterInvocation.class);

    /** 服务接口名 */
    private final String serviceName;

    /** 服务版本 */
    private final String version;

    /** RPC 请求 */
    private final RpcRequest request;

    /** 目标方法返回类型 */
    private final Class<?> returnType;

    /** 可用的服务实例列表 */
    private final List<ServiceInstance> instances;

    /** 负载均衡器 */
    private final LoadBalancer loadBalancer;

    /** Netty 客户端 */
    private final NettyClient nettyClient;

    /** 超时时间（毫秒） */
    private final long timeout;

    /** 重试次数 */
    private final int retries;

    // ==================== 熔断器配置 ====================

    /** 是否启用熔断器 */
    private final boolean enableCircuitBreaker;

    /** 熔断器错误率阈值 */
    private final int circuitBreakerThreshold;

    /** 熔断器恢复时间 */
    private final long circuitBreakerTimeout;

    // ==================== 限流器配置 ====================

    /** 是否启用限流 */
    private final boolean enableRateLimit;

    /** 限流阈值（每秒请求数） */
    private final int rateLimitPermits;

    public ClusterInvocation(String serviceName, String version, RpcRequest request,
                             Class<?> returnType, List<ServiceInstance> instances,
                             LoadBalancer loadBalancer, NettyClient nettyClient,
                             long timeout, int retries) {
        this(serviceName, version, request, returnType, instances, loadBalancer, nettyClient,
                timeout, retries, true, 50, 30000, false, 100);
    }

    public ClusterInvocation(String serviceName, String version, RpcRequest request,
                             Class<?> returnType, List<ServiceInstance> instances,
                             LoadBalancer loadBalancer, NettyClient nettyClient,
                             long timeout, int retries,
                             boolean enableCircuitBreaker, int circuitBreakerThreshold,
                             long circuitBreakerTimeout, boolean enableRateLimit, int rateLimitPermits) {
        this.serviceName = serviceName;
        this.version = version;
        this.request = request;
        this.returnType = returnType;
        this.instances = instances;
        this.loadBalancer = loadBalancer;
        this.nettyClient = nettyClient;
        this.timeout = timeout;
        this.retries = retries;
        this.enableCircuitBreaker = enableCircuitBreaker;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.circuitBreakerTimeout = circuitBreakerTimeout;
        this.enableRateLimit = enableRateLimit;
        this.rateLimitPermits = rateLimitPermits;
    }

    /**
     * 选择一个服务地址（支持预热权重）
     *
     * @param excluded 排除的地址列表（已失败的）
     * @return 选中的地址
     */
    public InetSocketAddress selectAddress(List<InetSocketAddress> excluded) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // 过滤排除的地址，保留 ServiceInstance 以便计算预热权重
        List<ServiceInstance> availableInstances = new java.util.ArrayList<>();
        for (ServiceInstance instance : instances) {
            InetSocketAddress addr = new InetSocketAddress(instance.getHost(), instance.getPort());
            if (excluded == null || !excluded.contains(addr)) {
                availableInstances.add(instance);
            }
        }

        if (availableInstances.isEmpty()) {
            return null;
        }

        // 使用支持预热的负载均衡选择
        return loadBalancer.selectInstance(availableInstances, serviceName);
    }

    /**
     * 选择一个服务实例（支持预热权重）
     *
     * @param excluded 排除的地址列表（已失败的）
     * @return 选中的服务实例
     */
    public ServiceInstance selectInstance(List<InetSocketAddress> excluded) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // 过滤排除的地址
        List<ServiceInstance> availableInstances = new java.util.ArrayList<>();
        for (ServiceInstance instance : instances) {
            InetSocketAddress addr = new InetSocketAddress(instance.getHost(), instance.getPort());
            if (excluded == null || !excluded.contains(addr)) {
                availableInstances.add(instance);
            }
        }

        if (availableInstances.isEmpty()) {
            return null;
        }

        // 如果只有一个实例，直接返回
        if (availableInstances.size() == 1) {
            return availableInstances.get(0);
        }

        // 加权随机选择（考虑预热权重）
        double[] weights = new double[availableInstances.size()];
        double totalWeight = 0.0;

        for (int i = 0; i < availableInstances.size(); i++) {
            ServiceInstance instance = availableInstances.get(i);
            double weight = instance.getWarmupWeight();
            weights[i] = weight;
            totalWeight += weight;
        }

        // 如果总权重为 0，给所有实例相等权重
        if (totalWeight <= 0) {
            return availableInstances.get((int) (Math.random() * availableInstances.size()));
        }

        // 加权随机选择
        double random = Math.random() * totalWeight;
        double cumulative = 0.0;

        for (int i = 0; i < availableInstances.size(); i++) {
            cumulative += weights[i];
            if (random < cumulative) {
                return availableInstances.get(i);
            }
        }

        return availableInstances.get(availableInstances.size() - 1);
    }

    /**
     * 获取所有可用地址
     */
    public List<InetSocketAddress> getAllAddresses() {
        List<InetSocketAddress> addresses = new java.util.ArrayList<>();
        for (ServiceInstance instance : instances) {
            addresses.add(new InetSocketAddress(instance.getHost(), instance.getPort()));
        }
        return addresses;
    }

    // Getters

    public String getServiceName() {
        return serviceName;
    }

    public String getVersion() {
        return version;
    }

    public RpcRequest getRequest() {
        return request;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public List<ServiceInstance> getInstances() {
        return instances;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public NettyClient getNettyClient() {
        return nettyClient;
    }

    public long getTimeout() {
        return timeout;
    }

    public int getRetries() {
        return retries;
    }

    public boolean isEnableCircuitBreaker() {
        return enableCircuitBreaker;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public long getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }

    public boolean isEnableRateLimit() {
        return enableRateLimit;
    }

    public int getRateLimitPermits() {
        return rateLimitPermits;
    }

    // ==================== 熔断/限流保护调用 ====================

    /**
     * 执行带熔断和限流保护的单次调用
     *
     * @param address 目标地址
     * @return RPC 响应
     * @throws Throwable 调用异常
     */
    public RpcResponse invokeWithProtection(InetSocketAddress address) throws Throwable {
        // 1. 限流检查
        if (enableRateLimit) {
            RateLimiter limiter = RateLimiterManager.getInstance().getRateLimiter(serviceName, rateLimitPermits);
            if (!limiter.tryAcquire()) {
                logger.warn("[RateLimit] Request rejected for service: {} (limit: {}/s)", serviceName, rateLimitPermits);
                throw new RateLimitException(serviceName, rateLimitPermits);
            }
        }

        // 2. 熔断检查
        CircuitBreaker circuitBreaker = null;
        if (enableCircuitBreaker) {
            CircuitBreakerManager cbManager = CircuitBreakerManager.getInstance();
            circuitBreaker = cbManager.getCircuitBreaker(serviceName, 100, circuitBreakerThreshold, circuitBreakerTimeout, 5);

            if (!circuitBreaker.allowRequest()) {
                logger.warn("[CircuitBreaker] Request blocked for service: {} (state: {})", serviceName, circuitBreaker.getState());
                throw new CircuitBreakerException(serviceName);
            }
        }

        // 3. 执行调用
        try {
            RpcResponse response = RpcInvoker.invoke(address, request, nettyClient, timeout);

            // 4. 记录成功
            if (circuitBreaker != null) {
                circuitBreaker.recordSuccess();
            }

            return response;

        } catch (Throwable e) {
            // 5. 记录失败
            if (circuitBreaker != null) {
                circuitBreaker.recordFailure();
            }
            throw e;
        }
    }
}