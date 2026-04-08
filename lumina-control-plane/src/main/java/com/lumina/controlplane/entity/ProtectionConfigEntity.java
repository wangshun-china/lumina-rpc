package com.lumina.controlplane.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 服务保护配置实体
 */
@Table("lumina_protection_config")
public class ProtectionConfigEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("service_name")
    private String serviceName;

    // 熔断器配置
    @Column("circuit_breaker_enabled")
    private Boolean circuitBreakerEnabled = true;

    @Column("circuit_breaker_threshold")
    private Integer circuitBreakerThreshold = 50;

    @Column("circuit_breaker_timeout")
    private Long circuitBreakerTimeout = 30000L;

    @Column("circuit_breaker_window_size")
    private Integer circuitBreakerWindowSize = 100;

    // 限流器配置
    @Column("rate_limiter_enabled")
    private Boolean rateLimiterEnabled = false;

    @Column("rate_limiter_permits")
    private Integer rateLimiterPermits = 100;

    // 集群配置
    @Column("cluster_strategy")
    private String clusterStrategy = "failover";

    @Column("retries")
    private Integer retries = 3;

    @Column("timeout_ms")
    private Long timeoutMs = 0L;

    // 元数据
    @Column("version")
    private Long version = 1L;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("description")
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Boolean getCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public void setCircuitBreakerEnabled(Boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public Integer getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public void setCircuitBreakerThreshold(Integer circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public Long getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }

    public void setCircuitBreakerTimeout(Long circuitBreakerTimeout) {
        this.circuitBreakerTimeout = circuitBreakerTimeout;
    }

    public Integer getCircuitBreakerWindowSize() {
        return circuitBreakerWindowSize;
    }

    public void setCircuitBreakerWindowSize(Integer circuitBreakerWindowSize) {
        this.circuitBreakerWindowSize = circuitBreakerWindowSize;
    }

    public Boolean getRateLimiterEnabled() {
        return rateLimiterEnabled;
    }

    public void setRateLimiterEnabled(Boolean rateLimiterEnabled) {
        this.rateLimiterEnabled = rateLimiterEnabled;
    }

    public Integer getRateLimiterPermits() {
        return rateLimiterPermits;
    }

    public void setRateLimiterPermits(Integer rateLimiterPermits) {
        this.rateLimiterPermits = rateLimiterPermits;
    }

    public String getClusterStrategy() {
        return clusterStrategy;
    }

    public void setClusterStrategy(String clusterStrategy) {
        this.clusterStrategy = clusterStrategy;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}