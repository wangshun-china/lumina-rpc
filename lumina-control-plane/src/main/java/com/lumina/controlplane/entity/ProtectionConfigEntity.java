package com.lumina.controlplane.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 服务保护配置实体
 *
 * 存储熔断器和限流器的动态配置
 * 服务级别粒度，按 Dubbo 做法
 */
@Entity
@Table(name = "lumina_protection_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"service_name"})
})
public class ProtectionConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务名称（唯一键） */
    @Column(name = "service_name", nullable = false, length = 255, unique = true)
    private String serviceName;

    // ==================== 熔断器配置 ====================

    /** 是否启用熔断器 */
    @Column(name = "circuit_breaker_enabled", nullable = false)
    private Boolean circuitBreakerEnabled = true;

    /** 熔断器错误率阈值（百分比） */
    @Column(name = "circuit_breaker_threshold")
    private Integer circuitBreakerThreshold = 50;

    /** 熔断器恢复时间（毫秒） */
    @Column(name = "circuit_breaker_timeout")
    private Long circuitBreakerTimeout = 30000L;

    /** 滑动窗口大小 */
    @Column(name = "circuit_breaker_window_size")
    private Integer circuitBreakerWindowSize = 100;

    // ==================== 限流器配置 ====================

    /** 是否启用限流器 */
    @Column(name = "rate_limiter_enabled", nullable = false)
    private Boolean rateLimiterEnabled = false;

    /** 限流阈值（每秒请求数） */
    @Column(name = "rate_limiter_permits")
    private Integer rateLimiterPermits = 100;

    // ==================== 运行时统计（不持久化） ====================

    /** 限流器通过数 */
    @Transient
    private Long rateLimiterPassed = 0L;

    /** 限流器拒绝数 */
    @Transient
    private Long rateLimiterRejected = 0L;

    /** 熔断器当前状态 */
    @Transient
    private String circuitBreakerState = "CLOSED";

    // ==================== 集群配置 ====================

    /** 默认集群策略 */
    @Column(name = "cluster_strategy", length = 50)
    private String clusterStrategy = "failover";

    /** 重试次数 */
    @Column(name = "retries")
    private Integer retries = 3;

    // ==================== 元数据 ====================

    /** 配置版本号（用于乐观锁） */
    @Column(name = "version")
    private Long version = 1L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "description", length = 500)
    private String description;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        version++;
    }

    // Getters and Setters

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

    public Long getRateLimiterPassed() {
        return rateLimiterPassed;
    }

    public void setRateLimiterPassed(Long rateLimiterPassed) {
        this.rateLimiterPassed = rateLimiterPassed;
    }

    public Long getRateLimiterRejected() {
        return rateLimiterRejected;
    }

    public void setRateLimiterRejected(Long rateLimiterRejected) {
        this.rateLimiterRejected = rateLimiterRejected;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(String circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
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