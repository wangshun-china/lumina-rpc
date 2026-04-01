package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.discovery.ServiceInstance;
import com.lumina.rpc.core.spi.LoadBalancer;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 集群调用上下文
 *
 * 封装一次 RPC 调用所需的所有信息
 * 集成熔断器和限流器保护
 * 使用默认序列化器（KRYO）进行消息编码
 *
 * 改进：
 * - 移除 ActiveCounter 相关逻辑（由 LoadBalancer 管理）
 * - selectAddress 方法委托给 LoadBalancer
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
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
}