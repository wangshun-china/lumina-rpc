package com.lumina.rpc.core.discovery;

import java.util.Objects;

/**
 * 服务实例
 * 表示一个可用的 RPC 服务提供者
 *
 * 支持服务预热：
 * - startTime: 实例启动时间戳
 * - warmupPeriod: 预热时间（毫秒）
 * - 预热期间权重从 0 逐渐增加到 1
 */
public class ServiceInstance {

    private String serviceName;
    private String host;
    private int port;
    private String version;
    private boolean healthy;

    /** 实例启动时间戳（毫秒） */
    private long startTime;

    /** 预热时间（毫秒），默认 60 秒 */
    private long warmupPeriod = 60000;

    /** 静态权重，默认 100（用于加权负载均衡） */
    private int weight = 100;

    public ServiceInstance() {
    }

    public ServiceInstance(String serviceName, String host, int port) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.version = "";
        this.healthy = true;
        this.startTime = System.currentTimeMillis();
    }

    public ServiceInstance(String serviceName, String host, int port, String version) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.version = version;
        this.healthy = true;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 计算预热权重
     *
     * @return 权重值 [0.0, 1.0]
     */
    public double getWarmupWeight() {
        if (warmupPeriod <= 0) {
            return 1.0; // 未配置预热，直接返回全量权重
        }

        long uptime = System.currentTimeMillis() - startTime;
        if (uptime >= warmupPeriod) {
            return 1.0; // 预热完成
        }

        // 线性增长
        return (double) uptime / warmupPeriod;
    }

    /**
     * 检查是否在预热期
     */
    public boolean isInWarmup() {
        if (warmupPeriod <= 0) {
            return false;
        }
        return System.currentTimeMillis() - startTime < warmupPeriod;
    }

    /**
     * 获取预热进度百分比
     */
    public int getWarmupProgress() {
        if (warmupPeriod <= 0) {
            return 100;
        }
        long uptime = System.currentTimeMillis() - startTime;
        int progress = (int) (uptime * 100 / warmupPeriod);
        return Math.min(100, Math.max(0, progress));
    }

    // Getters and Setters

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getWarmupPeriod() {
        return warmupPeriod;
    }

    public void setWarmupPeriod(long warmupPeriod) {
        this.warmupPeriod = warmupPeriod;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * 获取有效权重（静态权重 × 预热权重）
     *
     * @return 有效权重
     */
    public int getEffectiveWeight() {
        return (int) (weight * getWarmupWeight());
    }

    /**
     * 获取服务地址 key (host:port)
     */
    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "serviceName='" + serviceName + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", version='" + version + '\'' +
                ", healthy=" + healthy +
                ", startTime=" + startTime +
                ", warmupPeriod=" + warmupPeriod +
                ", warmupProgress=" + getWarmupProgress() + "%" +
                '}';
    }
}