package com.lumina.controlplane.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Span 实体
 *
 * 存储链路追踪的 Span 数据
 */
@Entity
@Table(name = "lumina_span", indexes = {
    @Index(name = "idx_trace_id", columnList = "trace_id"),
    @Index(name = "idx_service_name", columnList = "service_name"),
    @Index(name = "idx_start_time", columnList = "start_time")
})
public class SpanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Trace ID */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /** Span ID */
    @Column(name = "span_id", nullable = false, length = 64)
    private String spanId;

    /** 父 Span ID */
    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    /** 服务名称 */
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    /** 方法名称 */
    @Column(name = "method_name", length = 255)
    private String methodName;

    /** Span 类型：CLIENT / SERVER */
    @Column(name = "kind", length = 16)
    private String kind;

    /** 开始时间戳（毫秒） */
    @Column(name = "start_time")
    private Long startTime;

    /** 耗时（毫秒） */
    @Column(name = "duration")
    private Long duration;

    /** 是否成功 */
    @Column(name = "success")
    private Boolean success;

    /** 错误信息 */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /** 远程地址 */
    @Column(name = "remote_address", length = 128)
    private String remoteAddress;

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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}