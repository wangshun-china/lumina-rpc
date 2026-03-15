package com.lumina.controlplane.dto;

import com.lumina.controlplane.entity.SpanEntity;

import java.util.List;

/**
 * Trace 详情 DTO
 */
public class TraceDetailDto {

    private String traceId;
    private List<SpanEntity> spans;
    private long totalDuration;
    private int spanCount;
    private boolean hasError;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<SpanEntity> getSpans() {
        return spans;
    }

    public void setSpans(List<SpanEntity> spans) {
        this.spans = spans;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public int getSpanCount() {
        return spanCount;
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = spanCount;
    }

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }
}