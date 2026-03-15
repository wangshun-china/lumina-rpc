package com.lumina.controlplane.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Span DTO
 *
 * 用于接收 RPC 客户端上报的 Span 数据
 */
public class SpanDto {

    /** Trace ID */
    private String traceId;

    /** Span ID */
    private String spanId;

    /** 父 Span ID */
    private String parentSpanId;

    /** 服务名称 */
    private String serviceName;

    /** 方法名称 */
    private String methodName;

    /** Span 类型：CLIENT / SERVER */
    private String kind;

    /** 开始时间戳（毫秒） */
    private long startTime;

    /** 耗时（毫秒） */
    private long duration;

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
    private String errorMessage;

    /** 远程地址 */
    private String remoteAddress;

    /** 附加标签 */
    private Map<String, String> tags = new HashMap<>();

    // Getters and Setters

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

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
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

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}