package com.lumina.controlplane.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 保护统计数据实体
 *
 * 持久化存储熔断器和限流器的运行时统计数据
 * 解决服务重启后统计数据丢失的问题
 */
@Entity
@Table(name = "lumina_protection_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"service_name"})
})
public class ProtectionStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务名称（唯一键） */
    @Column(name = "service_name", nullable = false, length = 255, unique = true)
    private String serviceName;

    // ==================== 限流器统计 ====================

    /** 限流器通过数 */
    @Column(name = "rate_limiter_passed", nullable = false)
    private Long rateLimiterPassed = 0L;

    /** 限流器拒绝数 */
    @Column(name = "rate_limiter_rejected", nullable = false)
    private Long rateLimiterRejected = 0L;

    // ==================== 熔断器状态 ====================

    /** 熔断器当前状态: CLOSED, OPEN, HALF_OPEN */
    @Column(name = "circuit_breaker_state", length = 20)
    private String circuitBreakerState = "CLOSED";

    /** 熔断器打开次数 */
    @Column(name = "circuit_breaker_open_count")
    private Integer circuitBreakerOpenCount = 0;

    /** 最后一次熔断时间 */
    @Column(name = "last_trip_time")
    private LocalDateTime lastTripTime;

    // ==================== 时间戳 ====================

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public Integer getCircuitBreakerOpenCount() {
        return circuitBreakerOpenCount;
    }

    public void setCircuitBreakerOpenCount(Integer circuitBreakerOpenCount) {
        this.circuitBreakerOpenCount = circuitBreakerOpenCount;
    }

    public LocalDateTime getLastTripTime() {
        return lastTripTime;
    }

    public void setLastTripTime(LocalDateTime lastTripTime) {
        this.lastTripTime = lastTripTime;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}