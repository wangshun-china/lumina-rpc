package com.lumina.controlplane.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 服务实例实体
 */
@Table("lumina_service_instance")
public class ServiceInstanceEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("service_name")
    private String serviceName;

    @Column("instance_id")
    private String instanceId;

    @Column("host")
    private String host;

    @Column("port")
    private Integer port;

    @Column("status")
    private String status = "UP";

    @Column("version")
    private String version;

    @Column("metadata")
    private String metadata;

    @Column("service_metadata")
    private String serviceMetadata;

    @Column("start_time")
    private Long startTime;

    @Column("warmup_period")
    private Long warmupPeriod = 60000L;

    @Column("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column("registered_at")
    private LocalDateTime registeredAt;

    @Column("expires_at")
    private LocalDateTime expiresAt;

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

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getServiceMetadata() {
        return serviceMetadata;
    }

    public void setServiceMetadata(String serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getWarmupPeriod() {
        return warmupPeriod;
    }

    public void setWarmupPeriod(Long warmupPeriod) {
        this.warmupPeriod = warmupPeriod;
    }

    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isHealthy() {
        return "UP".equals(status) && !isExpired();
    }

    public double getWarmupWeight() {
        if (warmupPeriod == null || warmupPeriod <= 0) {
            return 1.0;
        }
        if (startTime == null) {
            return 1.0;
        }
        long uptime = System.currentTimeMillis() - startTime;
        if (uptime >= warmupPeriod) {
            return 1.0;
        }
        return (double) uptime / warmupPeriod;
    }

    public boolean isInWarmup() {
        if (warmupPeriod == null || warmupPeriod <= 0 || startTime == null) {
            return false;
        }
        return System.currentTimeMillis() - startTime < warmupPeriod;
    }

    public int getWarmupProgress() {
        if (warmupPeriod == null || warmupPeriod <= 0 || startTime == null) {
            return 100;
        }
        long uptime = System.currentTimeMillis() - startTime;
        int progress = (int) (uptime * 100 / warmupPeriod);
        return Math.min(100, Math.max(0, progress));
    }
}