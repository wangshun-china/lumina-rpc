package com.lumina.rpc.core.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求统计上报器
 *
 * 收集 RPC 调用统计并上报到控制面
 */
public class RequestStatsReporter {

    private static final Logger logger = LoggerFactory.getLogger(RequestStatsReporter.class);

    private static volatile RequestStatsReporter instance;

    private final String controlPlaneUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 待上报的统计数据 */
    private final ConcurrentHashMap<String, StatsBuffer> statsBuffers = new ConcurrentHashMap<>();

    /** 定时上报线程 */
    private ScheduledExecutorService scheduler;

    private RequestStatsReporter(String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static RequestStatsReporter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RequestStatsReporter not initialized");
        }
        return instance;
    }

    public static synchronized void initialize(String controlPlaneUrl) {
        if (instance == null) {
            instance = new RequestStatsReporter(controlPlaneUrl);
            logger.info("RequestStatsReporter initialized with control plane: {}", controlPlaneUrl);
        }
    }

    /**
     * 启动定时上报
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "request-stats-reporter");
            t.setDaemon(true);
            return t;
        });

        // 每5秒上报一次
        scheduler.scheduleAtFixedRate(
                this::flushStats,
                5, 5, TimeUnit.SECONDS
        );

        logger.info("Request stats reporter started");
    }

    /**
     * 停止上报
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 记录请求
     */
    public void recordRequest(String serviceName, boolean success, long latencyMs) {
        StatsBuffer buffer = statsBuffers.computeIfAbsent(serviceName, k -> new StatsBuffer());

        if (success) {
            buffer.successCount.incrementAndGet();
        } else {
            buffer.failCount.incrementAndGet();
        }
        buffer.totalLatency.addAndGet(latencyMs);
    }

    /**
     * 上报统计数据
     */
    private void flushStats() {
        if (statsBuffers.isEmpty()) {
            return;
        }

        List<Map<String, Object>> batch = new ArrayList<>();

        // 收集所有服务的统计数据
        for (Map.Entry<String, StatsBuffer> entry : statsBuffers.entrySet()) {
            String serviceName = entry.getKey();
            StatsBuffer buffer = entry.getValue();

            long success = buffer.successCount.getAndSet(0);
            long fail = buffer.failCount.getAndSet(0);
            long latency = buffer.totalLatency.getAndSet(0);

            if (success + fail > 0) {
                // 上报聚合数据
                long avgLatency = (success + fail) > 0 ? latency / (success + fail) : 0;

                batch.add(Map.of(
                        "serviceName", serviceName,
                        "successCount", success,
                        "failCount", fail,
                        "avgLatency", avgLatency
                ));
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        // 异步上报
        try {
            String body = objectMapper.writeValueAsString(batch);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(controlPlaneUrl + "/api/v1/stats/report/aggregate"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            logger.debug("Reported {} service stats", batch.size());
                        }
                    })
                    .exceptionally(e -> {
                        logger.debug("Failed to report stats: {}", e.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            logger.debug("Failed to serialize stats: {}", e.getMessage());
        }
    }

    /**
     * 统计缓冲区
     */
    private static class StatsBuffer {
        final AtomicLong successCount = new AtomicLong(0);
        final AtomicLong failCount = new AtomicLong(0);
        final AtomicLong totalLatency = new AtomicLong(0);
    }
}