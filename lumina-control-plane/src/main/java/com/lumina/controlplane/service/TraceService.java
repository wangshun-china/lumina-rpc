package com.lumina.controlplane.service;

import com.lumina.controlplane.dto.SpanDto;
import com.lumina.controlplane.entity.SpanEntity;
import com.lumina.controlplane.repository.SpanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路追踪服务
 */
@Service
public class TraceService {

    private static final Logger logger = LoggerFactory.getLogger(TraceService.class);

    @Autowired
    private SpanRepository spanRepository;

    /**
     * 保存 Span
     */
    @Transactional
    public void saveSpan(SpanDto span) {
        SpanEntity entity = new SpanEntity();
        entity.setTraceId(span.getTraceId());
        entity.setSpanId(span.getSpanId());
        entity.setParentSpanId(span.getParentSpanId());
        entity.setServiceName(span.getServiceName());
        entity.setMethodName(span.getMethodName());
        entity.setKind(span.getKind());
        entity.setStartTime(span.getStartTime());
        entity.setDuration(span.getDuration());
        entity.setSuccess(span.isSuccess());
        entity.setErrorMessage(span.getErrorMessage());
        entity.setRemoteAddress(span.getRemoteAddress());

        spanRepository.save(entity);

        logger.debug("Saved span: {} (traceId: {})", span.getSpanId(), span.getTraceId());
    }

    /**
     * 获取 Trace 详情（包含所有 Span）
     */
    public TraceDetail getTraceDetail(String traceId) {
        List<SpanEntity> spans = spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);

        if (spans.isEmpty()) {
            return null;
        }

        TraceDetail detail = new TraceDetail();
        detail.setTraceId(traceId);
        detail.setSpans(spans);

        // 计算统计信息
        long totalDuration = 0;
        int spanCount = spans.size();
        boolean hasError = false;

        for (SpanEntity span : spans) {
            if (span.getDuration() != null && span.getDuration() > totalDuration) {
                totalDuration = span.getDuration();
            }
            if (Boolean.FALSE.equals(span.getSuccess())) {
                hasError = true;
            }
        }

        detail.setTotalDuration(totalDuration);
        detail.setSpanCount(spanCount);
        detail.setHasError(hasError);

        return detail;
    }

    /**
     * 获取最近的 Trace 列表
     */
    public List<TraceSummary> getRecentTraces(int limit) {
        List<Object[]> results = spanRepository.findRecentTraceIdsWithTime(PageRequest.of(0, limit));
        List<TraceSummary> summaries = new ArrayList<>();

        for (Object[] row : results) {
            String traceId = (String) row[0];
            Long maxStartTime = row[1] != null ? ((Number) row[1]).longValue() : 0L;

            TraceDetail detail = getTraceDetail(traceId);
            if (detail != null) {
                TraceSummary summary = new TraceSummary();
                summary.setTraceId(traceId);
                summary.setSpanCount(detail.getSpanCount());
                summary.setTotalDuration(detail.getTotalDuration());
                summary.setHasError(detail.isHasError());
                summary.setStartTime(maxStartTime);

                // 获取第一个 Span 的信息
                if (!detail.getSpans().isEmpty()) {
                    SpanEntity firstSpan = detail.getSpans().get(0);
                    summary.setServiceName(firstSpan.getServiceName());
                }

                summaries.add(summary);
            }
        }

        return summaries;
    }

    /**
     * 获取服务调用统计
     */
    public List<ServiceStats> getServiceStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<SpanEntity> spans = spanRepository.findByServiceNameAndTimeRange("", startTime, endTime);

        // 按服务名分组统计
        Map<String, ServiceStats> statsMap = new HashMap<>();

        for (SpanEntity span : spans) {
            String serviceName = span.getServiceName();
            ServiceStats stats = statsMap.computeIfAbsent(serviceName, k -> {
                ServiceStats s = new ServiceStats();
                s.setServiceName(serviceName);
                return s;
            });

            stats.incrementCallCount();
            if (Boolean.TRUE.equals(span.getSuccess())) {
                stats.incrementSuccessCount();
            } else {
                stats.incrementErrorCount();
            }
            if (span.getDuration() != null) {
                stats.addDuration(span.getDuration());
            }
        }

        return new ArrayList<>(statsMap.values());
    }

    /**
     * Trace 详情
     */
    public static class TraceDetail {
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

    /**
     * Trace 摘要
     */
    public static class TraceSummary {
        private String traceId;
        private String serviceName;
        private long startTime;
        private int spanCount;
        private long totalDuration;
        private boolean hasError;

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public int getSpanCount() {
            return spanCount;
        }

        public void setSpanCount(int spanCount) {
            this.spanCount = spanCount;
        }

        public long getTotalDuration() {
            return totalDuration;
        }

        public void setTotalDuration(long totalDuration) {
            this.totalDuration = totalDuration;
        }

        public boolean isHasError() {
            return hasError;
        }

        public void setHasError(boolean hasError) {
            this.hasError = hasError;
        }
    }

    /**
     * 服务统计
     */
    public static class ServiceStats {
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
}