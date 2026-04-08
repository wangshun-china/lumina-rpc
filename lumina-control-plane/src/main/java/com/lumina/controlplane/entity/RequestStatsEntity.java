package com.lumina.controlplane.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 请求统计实体
 */
@Table("lumina_request_stats")
public class RequestStatsEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("service_name")
    private String serviceName;

    @Column("stat_time")
    private LocalDateTime statTime;

    @Column("total_requests")
    private Long totalRequests = 0L;

    @Column("success_count")
    private Long successCount = 0L;

    @Column("fail_count")
    private Long failCount = 0L;

    @Column("total_latency")
    private Long totalLatency = 0L;

    @Column("max_latency")
    private Long maxLatency = 0L;

    @Column("min_latency")
    private Long minLatency = 0L;

    @Column("created_at")
    private LocalDateTime createdAt;

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

    public Long getAvgLatency() {
        if (totalRequests == null || totalRequests == 0 || totalLatency == null) {
            return 0L;
        }
        return totalLatency / totalRequests;
    }

    public Double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 100.0;
        }
        return (double) successCount / totalRequests * 100;
    }
}