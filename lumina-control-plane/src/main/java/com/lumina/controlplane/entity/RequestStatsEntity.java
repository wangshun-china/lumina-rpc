package com.lumina.controlplane.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 请求统计实体
 *
 * 按分钟粒度记录请求统计数据
 */
@Entity
@Table(name = "lumina_request_stats", indexes = {
    @Index(name = "idx_service_time", columnList = "service_name, stat_time")
})
public class RequestStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务名称 */
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    /** 统计时间（分钟粒度） */
    @Column(name = "stat_time", nullable = false)
    private LocalDateTime statTime;

    /** 总请求数 */
    @Column(name = "total_requests", nullable = false)
    private Long totalRequests = 0L;

    /** 成功请求数 */
    @Column(name = "success_count", nullable = false)
    private Long successCount = 0L;

    /** 失败请求数 */
    @Column(name = "fail_count", nullable = false)
    private Long failCount = 0L;

    /** 总响应时间（毫秒） */
    @Column(name = "total_latency")
    private Long totalLatency = 0L;

    /** 最大响应时间 */
    @Column(name = "max_latency")
    private Long maxLatency = 0L;

    /** 最小响应时间 */
    @Column(name = "min_latency")
    private Long minLatency = 0L;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

    public LocalDateTime getStatTime() {
        return statTime;
    }

    public void setStatTime(LocalDateTime statTime) {
        this.statTime = statTime;
    }

    public Long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(Long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getFailCount() {
        return failCount;
    }

    public void setFailCount(Long failCount) {
        this.failCount = failCount;
    }

    public Long getTotalLatency() {
        return totalLatency;
    }

    public void setTotalLatency(Long totalLatency) {
        this.totalLatency = totalLatency;
    }

    public Long getMaxLatency() {
        return maxLatency;
    }

    public void setMaxLatency(Long maxLatency) {
        this.maxLatency = maxLatency;
    }

    public Long getMinLatency() {
        return minLatency;
    }

    public void setMinLatency(Long minLatency) {
        this.minLatency = minLatency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 计算平均响应时间
     */
    public Long getAvgLatency() {
        if (totalRequests == null || totalRequests == 0 || totalLatency == null) {
            return 0L;
        }
        return totalLatency / totalRequests;
    }

    /**
     * 计算成功率
     */
    public Double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 100.0;
        }
        return (double) successCount / totalRequests * 100;
    }
}