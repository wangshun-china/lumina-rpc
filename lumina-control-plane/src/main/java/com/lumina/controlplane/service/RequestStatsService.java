package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.RequestStatsEntity;
import com.lumina.controlplane.repository.RequestStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求统计服务
 *
 * 收集和聚合 RPC 请求统计数据
 */
@Service
public class RequestStatsService {

    private static final Logger logger = LoggerFactory.getLogger(RequestStatsService.class);

    private final RequestStatsRepository repository;

    /** 内存中的实时统计（服务 -> 统计数据） */
    private final ConcurrentHashMap<String, ServiceStats> currentStats = new ConcurrentHashMap<>();

    /** 当前统计时间窗口 */
    private volatile LocalDateTime currentWindowStart;

    public RequestStatsService(RequestStatsRepository repository) {
        this.repository = repository;
        this.currentWindowStart = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * 服务统计数据（内存）
     */
    private static class ServiceStats {
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong successCount = new AtomicLong(0);
        final AtomicLong failCount = new AtomicLong(0);
        final AtomicLong totalLatency = new AtomicLong(0);
        final AtomicLong maxLatency = new AtomicLong(0);
        final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    }

    /**
     * 记录请求
     */
    public void recordRequest(String serviceName, boolean success, long latencyMs) {
        ServiceStats stats = currentStats.computeIfAbsent(serviceName, k -> new ServiceStats());

        stats.totalRequests.incrementAndGet();
        stats.totalLatency.addAndGet(latencyMs);

        if (success) {
            stats.successCount.incrementAndGet();
        } else {
            stats.failCount.incrementAndGet();
        }

        // 更新最大最小延迟
        long currentMax = stats.maxLatency.get();
        while (latencyMs > currentMax) {
            if (stats.maxLatency.compareAndSet(currentMax, latencyMs)) {
                break;
            }
            currentMax = stats.maxLatency.get();
        }

        long currentMin = stats.minLatency.get();
        while (latencyMs < currentMin) {
            if (stats.minLatency.compareAndSet(currentMin, latencyMs)) {
                break;
            }
            currentMin = stats.minLatency.get();
        }
    }

    /**
     * 记录聚合统计数据（Consumer 端上报）
     */
    public void recordAggregate(String serviceName, long successCount, long failCount, long avgLatency) {
        ServiceStats stats = currentStats.computeIfAbsent(serviceName, k -> new ServiceStats());

        long totalCount = successCount + failCount;
        stats.totalRequests.addAndGet(totalCount);
        stats.successCount.addAndGet(successCount);
        stats.failCount.addAndGet(failCount);
        stats.totalLatency.addAndGet(avgLatency * totalCount);

        // 更新最大最小延迟（使用平均值作为近似）
        if (avgLatency > 0) {
            long currentMax = stats.maxLatency.get();
            while (avgLatency > currentMax) {
                if (stats.maxLatency.compareAndSet(currentMax, avgLatency)) {
                    break;
                }
                currentMax = stats.maxLatency.get();
            }

            long currentMin = stats.minLatency.get();
            while (avgLatency < currentMin) {
                if (stats.minLatency.compareAndSet(currentMin, avgLatency)) {
                    break;
                }
                currentMin = stats.minLatency.get();
            }
        }

        logger.debug("Recorded aggregate stats for {}: success={}, fail={}, latency={}ms",
                serviceName, successCount, failCount, avgLatency);
    }

    /**
     * 定时持久化统计数据（每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void flushStats() {
        if (currentStats.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        // 持久化当前窗口的数据
        int serviceCount = currentStats.size();
        for (Map.Entry<String, ServiceStats> entry : currentStats.entrySet()) {
            String serviceName = entry.getKey();
            ServiceStats stats = entry.getValue();

            RequestStatsEntity entity = new RequestStatsEntity();
            entity.setServiceName(serviceName);
            entity.setStatTime(currentWindowStart);
            entity.setTotalRequests(stats.totalRequests.get());
            entity.setSuccessCount(stats.successCount.get());
            entity.setFailCount(stats.failCount.get());
            entity.setTotalLatency(stats.totalLatency.get());
            entity.setMaxLatency(stats.maxLatency.get());
            entity.setMinLatency(stats.minLatency.get() == Long.MAX_VALUE ? 0 : stats.minLatency.get());

            repository.save(entity);
            logger.debug("Flushed stats for {}: {} requests", serviceName, stats.totalRequests.get());
        }

        // 重置统计
        currentStats.clear();
        currentWindowStart = now;

        if (serviceCount > 0) {
            logger.info("Flushed request stats for {} services", serviceCount);
        }
    }

    /**
     * 获取趋势数据（最近N分钟）
     */
    public List<Map<String, Object>> getTrendData(int minutes) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startTime = endTime.minusMinutes(minutes);

        List<Object[]> results = repository.aggregateByTime(startTime, endTime);

        List<Map<String, Object>> trendData = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("time", row[0]);
            item.put("totalRequests", row[1]);
            item.put("successCount", row[2]);
            item.put("failCount", row[3]);
            item.put("totalLatency", row[4]);

            // 计算平均延迟
            Long total = (Long) row[1];
            Long latency = (Long) row[4];
            item.put("avgLatency", total > 0 ? latency / total : 0);

            trendData.add(item);
        }

        // 添加当前窗口的实时数据
        if (!currentStats.isEmpty()) {
            Map<String, Object> currentData = new HashMap<>();
            currentData.put("time", currentWindowStart);

            long totalRequests = 0;
            long successCount = 0;
            long failCount = 0;
            long totalLatency = 0;

            for (ServiceStats stats : currentStats.values()) {
                totalRequests += stats.totalRequests.get();
                successCount += stats.successCount.get();
                failCount += stats.failCount.get();
                totalLatency += stats.totalLatency.get();
            }

            currentData.put("totalRequests", totalRequests);
            currentData.put("successCount", successCount);
            currentData.put("failCount", failCount);
            currentData.put("totalLatency", totalLatency);
            currentData.put("avgLatency", totalRequests > 0 ? totalLatency / totalRequests : 0);

            trendData.add(currentData);
        }

        return trendData;
    }

    /**
     * 获取服务统计
     */
    public List<Map<String, Object>> getServiceStats(int minutes) {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startTime = endTime.minusMinutes(minutes);

        List<Object[]> results = repository.aggregateByService(startTime, endTime);

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

    /**
     * 获取实时统计（内存中）
     */
    public Map<String, Object> getRealtimeStats() {
        Map<String, Object> result = new HashMap<>();

        long totalRequests = 0;
        long totalSuccess = 0;
        long totalFail = 0;
        long totalLatency = 0;

        for (Map.Entry<String, ServiceStats> entry : currentStats.entrySet()) {
            ServiceStats stats = entry.getValue();
            totalRequests += stats.totalRequests.get();
            totalSuccess += stats.successCount.get();
            totalFail += stats.failCount.get();
            totalLatency += stats.totalLatency.get();
        }

        result.put("totalRequests", totalRequests);
        result.put("successCount", totalSuccess);
        result.put("failCount", totalFail);
        result.put("avgLatency", totalRequests > 0 ? totalLatency / totalRequests : 0);
        result.put("serviceCount", currentStats.size());

        return result;
    }

    /**
     * 清理旧数据（保留最近24小时）
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行
    @Transactional
    public void cleanupOldData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        repository.deleteByStatTimeBefore(cutoff);
        logger.info("Cleaned up request stats before {}", cutoff);
    }
}