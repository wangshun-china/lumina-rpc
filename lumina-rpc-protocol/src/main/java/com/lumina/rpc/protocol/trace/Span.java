package com.lumina.rpc.protocol.trace;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Span 数据结构
 *
 * 表示一次 RPC 调用的追踪信息
 */
public class Span implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Trace ID（整个调用链的唯一标识） */
    private String traceId;

    /** Span ID（当前调用的唯一标识） */
    private String spanId;

    /** 父 Span ID（上级调用的标识） */
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

    public Span() {
    }

    public Span(String traceId, String spanId, String parentSpanId, String serviceName,
                String methodName, String kind) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.kind = kind;
        this.startTime = System.currentTimeMillis();
        this.success = true;
    }

    /**
     * 计算并设置耗时
     */
    public void finish() {
        this.duration = System.currentTimeMillis() - startTime;
    }

    /**
     * 设置错误信息
     */
    public void error(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }

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

    @Override
    public String toString() {
        return "Span{" +
                "traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                ", parentSpanId='" + parentSpanId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", kind='" + kind + '\'' +
                ", duration=" + duration + "ms" +
                ", success=" + success +
                '}';
    }
}