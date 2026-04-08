package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.RequestStatsEntity;
import com.lumina.controlplane.mapper.RequestStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 请求统计服务
 */
@Service
public class RequestStatsService {

    private static final Logger logger = LoggerFactory.getLogger(RequestStatsService.class);

    private final RequestStatsMapper mapper;

    public RequestStatsService(RequestStatsMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void recordAggregate(String serviceName, long successCount, long failCount, long avgLatency) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<RequestStatsEntity> existing = mapper.findByServiceNameAndTimeRange(
                serviceName, now, now.plusMinutes(1));

        RequestStatsEntity entity;
        if (existing.isEmpty()) {
            entity = new RequestStatsEntity();
            entity.setServiceName(serviceName);
            entity.setStatTime(now);
            entity.setTotalRequests(successCount + failCount);
            entity.setSuccessCount(successCount);
            entity.setFailCount(failCount);
            entity.setTotalLatency(avgLatency * (successCount + failCount));
            entity.setMaxLatency(avgLatency);
            entity.setMinLatency(avgLatency > 0 ? avgLatency : 0);
            entity.setCreatedAt(LocalDateTime.now());
            mapper.insert(entity);
        } else {
            entity = existing.get(0);
            long totalCount = successCount + failCount;
            entity.setTotalRequests(entity.getTotalRequests() + totalCount);
            entity.setSuccessCount(entity.getSuccessCount() + successCount);
            entity.setFailCount(entity.getFailCount() + failCount);
            entity.setTotalLatency(entity.getTotalLatency() + avgLatency * totalCount);

            if (avgLatency > 0) {
                if (entity.getMaxLatency() == null || avgLatency > entity.getMaxLatency()) {
                    entity.setMaxLatency(avgLatency);
                }
                if (entity.getMinLatency() == null || entity.getMinLatency() == 0 || avgLatency < entity.getMinLatency()) {
                    entity.setMinLatency(avgLatency);
                }
            }
            mapper.update(entity);
        }

        logger.debug("Recorded aggregate stats for {}: success={}, fail={}, latency={}ms",
                serviceName, successCount, failCount, avgLatency);
    }

    @Transactional
    public void recordRequest(String serviceName, boolean success, long latencyMs) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<RequestStatsEntity> existing = mapper.findByServiceNameAndTimeRange(
                serviceName, now, now.plusMinutes(1));

        RequestStatsEntity entity;
        if (existing.isEmpty()) {
            entity = new RequestStatsEntity();
            entity.setServiceName(serviceName);
            entity.setStatTime(now);
            entity.setTotalRequests(1L);
            entity.setSuccessCount(success ? 1L : 0L);
            entity.setFailCount(success ? 0L : 1L);
            entity.setTotalLatency(latencyMs);
            entity.setMaxLatency(latencyMs);
            entity.setMinLatency(latencyMs);
            entity.setCreatedAt(LocalDateTime.now());
            mapper.insert(entity);
        } else {
            entity = existing.get(0);
            entity.setTotalRequests(entity.getTotalRequests() + 1);
            if (success) {
                entity.setSuccessCount(entity.getSuccessCount() + 1);
            } else {
                entity.setFailCount(entity.getFailCount() + 1);
            }
            entity.setTotalLatency(entity.getTotalLatency() + latencyMs);

            if (latencyMs > entity.getMaxLatency()) {
                entity.setMaxLatency(latencyMs);
            }
            if (entity.getMinLatency() == null || entity.getMinLatency() == 0 || latencyMs < entity.getMinLatency()) {
                entity.setMinLatency(latencyMs);
            }
            mapper.update(entity);
        }
    }

    public List<Map<String, Object>> getTrendData(int minutes) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startTime = endTime.minusMinutes(minutes);

        List<Object[]> results = mapper.aggregateByTime(startTime, endTime);

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("time", row[0]);
            item.put("totalRequests", row[1]);
            item.put("successCount", row[2]);
            item.put("failCount", row[3]);
            item.put("totalLatency", row[4]);

            Long total = (Long) row[1];
            Long latency = (Long) row[4];
            item.put("avgLatency", total > 0 ? latency / total : 0);

            trendData.add(item);
        }

        return trendData;
    }

    public List<Map<String, Object>> getServiceStats(int minutes) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startTime = endTime.minusMinutes(minutes);

        List<Object[]> results = mapper.aggregateByService(startTime, endTime);

        List<Map<String, Object>> serviceStats = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("serviceName", row[0]);
            item.put("totalRequests", row[1]);
            item.put("successCount", row[2]);
            item.put("failCount", row[3]);
            item.put("avgLatency", row[4]);
            serviceStats.add(item);
        }

        return serviceStats;
    }

    public Map<String, Object> getRealtimeStats() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(5);

        List<Object[]> dbResults = mapper.aggregateByTime(startTime, endTime);

        long totalRequests = 0;
        long totalSuccess = 0;
        long totalFail = 0;
        long totalLatency = 0;

        for (Object[] row : dbResults) {
            totalRequests += ((Number) row[1]).longValue();
            totalSuccess += ((Number) row[2]).longValue();
            totalFail += ((Number) row[3]).longValue();
            totalLatency += ((Number) row[4]).longValue();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalRequests", totalRequests);
        result.put("successCount", totalSuccess);
        result.put("failCount", totalFail);
        result.put("avgLatency", totalRequests > 0 ? totalLatency / totalRequests : 0);

        return result;
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        mapper.deleteByStatTimeBefore(cutoff);
        logger.info("Cleaned up request stats before {}", cutoff);
    }
}