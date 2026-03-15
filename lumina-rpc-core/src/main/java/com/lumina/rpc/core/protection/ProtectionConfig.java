package com.lumina.rpc.core.protection;

/**
 * 保护配置
 *
 * 熔断器和限流器的配置对象
 */
public class ProtectionConfig {

    /** 服务名称 */
    private String serviceName;

    // ==================== 熔断器配置 ====================

    /** 是否启用熔断器 */
    private boolean circuitBreakerEnabled = true;

    /** 熔断器错误率阈值（百分比） */
    private int circuitBreakerThreshold = 50;

    /** 熔断器恢复时间（毫秒） */
    private long circuitBreakerTimeout = 30000L;

    // ==================== 限流器配置 ====================

    /** 是否启用限流器 */
    private boolean rateLimiterEnabled = false;

    /** 限流阈值（每秒请求数） */
    private int rateLimiterPermits = 100;

    // ==================== 集群配置 ====================

    /** 集群策略 */
    private String clusterStrategy = "failover";

    /** 重试次数 */
    private int retries = 3;

    /** 超时时间（毫秒），0 表示使用注解默认值 */
    private long timeout = 0;

    public ProtectionConfig() {
    }

    public ProtectionConfig(String serviceName) {
        this.serviceName = serviceName;
    }

    // Getters and Setters

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public long getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }

    public void setCircuitBreakerTimeout(long circuitBreakerTimeout) {
        this.circuitBreakerTimeout = circuitBreakerTimeout;
    }

    public boolean isRateLimiterEnabled() {
        return rateLimiterEnabled;
    }

    public void setRateLimiterEnabled(boolean rateLimiterEnabled) {
        this.rateLimiterEnabled = rateLimiterEnabled;
    }

    public int getRateLimiterPermits() {
        return rateLimiterPermits;
    }

    public void setRateLimiterPermits(int rateLimiterPermits) {
        this.rateLimiterPermits = rateLimiterPermits;
    }

    public String getClusterStrategy() {
        return clusterStrategy;
    }

    public void setClusterStrategy(String clusterStrategy) {
        this.clusterStrategy = clusterStrategy;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "ProtectionConfig{" +
                "serviceName='" + serviceName + '\'' +
                ", circuitBreakerEnabled=" + circuitBreakerEnabled +
                ", circuitBreakerThreshold=" + circuitBreakerThreshold +
                ", rateLimiterEnabled=" + rateLimiterEnabled +
                ", rateLimiterPermits=" + rateLimiterPermits +
                ", clusterStrategy='" + clusterStrategy + '\'' +
                '}';
    }
}