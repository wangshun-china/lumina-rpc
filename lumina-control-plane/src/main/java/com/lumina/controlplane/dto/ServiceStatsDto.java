package com.lumina.controlplane.dto;

/**
 * 服务统计 DTO
 */
public class ServiceStatsDto {

    private String serviceName;
    private int callCount;
    private int successCount;
    private int errorCount;
    private long totalDuration;
    private long maxDuration;
    private long minDuration = Long.MAX_VALUE;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getCallCount() {
        return callCount;
    }

    public void incrementCallCount() {
        this.callCount++;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void incrementErrorCount() {
        this.errorCount++;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public void addDuration(long duration) {
        this.totalDuration += duration;
        if (duration > maxDuration) {
            maxDuration = duration;
        }
        if (duration < minDuration) {
            minDuration = duration;
        }
    }

    public long getAvgDuration() {
        return callCount > 0 ? totalDuration / callCount : 0;
    }

    public long getMaxDuration() {
        return maxDuration;
    }

    public long getMinDuration() {
        return minDuration == Long.MAX_VALUE ? 0 : minDuration;
    }
}