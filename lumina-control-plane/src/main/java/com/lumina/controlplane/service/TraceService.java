package com.lumina.controlplane.service;

import com.lumina.controlplane.dto.SpanDto;
import com.lumina.controlplane.dto.TraceDetailDto;
import com.lumina.controlplane.dto.TraceSummaryDto;
import com.lumina.controlplane.dto.ServiceStatsDto;
import com.lumina.controlplane.entity.SpanEntity;
import com.lumina.controlplane.mapper.SpanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private SpanMapper spanMapper;

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
        entity.setCreatedAt(LocalDateTime.now());

        spanMapper.insert(entity);

        logger.debug("Saved span: {} (traceId: {})", span.getSpanId(), span.getTraceId());
    }

    public TraceDetailDto getTraceDetail(String traceId) {
        List<SpanEntity> spans = spanMapper.findByTraceIdOrderByStartTimeAsc(traceId);

        if (spans.isEmpty()) {
            return null;
        }

        TraceDetailDto detail = new TraceDetailDto();
        detail.setTraceId(traceId);
        detail.setSpans(spans);

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

    public List<TraceSummaryDto> getRecentTraces(int limit) {
        List<Object[]> results = spanMapper.findRecentTraceIdsWithTime(limit);
        List<TraceSummaryDto> summaries = new ArrayList<>();

        for (Object[] row : results) {
            String traceId = (String) row[0];
            Long maxStartTime = row[1] != null ? ((Number) row[1]).longValue() : 0L;

            TraceDetailDto detail = getTraceDetail(traceId);
            if (detail != null) {
                TraceSummaryDto summary = new TraceSummaryDto();
                summary.setTraceId(traceId);
                summary.setSpanCount(detail.getSpanCount());
                summary.setTotalDuration(detail.getTotalDuration());
                summary.setHasError(detail.isHasError());
                summary.setStartTime(maxStartTime);

                if (!detail.getSpans().isEmpty()) {
                    SpanEntity firstSpan = detail.getSpans().get(0);
                    summary.setServiceName(firstSpan.getServiceName());
                }

                summaries.add(summary);
            }
        }

        return summaries;
    }

    public List<ServiceStatsDto> getServiceStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<SpanEntity> spans = spanMapper.findByServiceNameAndTimeRange("", startTime, endTime);

        Map<String, ServiceStatsDto> statsMap = new HashMap<>();

        for (SpanEntity span : spans) {
            String serviceName = span.getServiceName();
            ServiceStatsDto stats = statsMap.computeIfAbsent(serviceName, k -> {
                ServiceStatsDto s = new ServiceStatsDto();
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
}