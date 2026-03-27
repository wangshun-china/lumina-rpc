package com.lumina.rpc.core.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock 规则订阅客户端 - 统一 SSE 实现（HttpClient 异步流）
 *
 * 与 ProtectionConfigClient 统一架构：
 * 1. 使用 HttpClient + BodyHandlers.ofLines() 处理 SSE 流
 * 2. 异步 CompletableFuture 处理
 * 3. 统一事件格式：config-change {type: "mock", serviceName, data}
 * 4. 自动重连 + 定时兜底刷新
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
public class MockRuleSubscriptionClient {

    private static final Logger logger = LoggerFactory.getLogger(MockRuleSubscriptionClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Control Plane 地址
    private static String controlPlaneUrl = "http://localhost:8080";

    // HttpClient（复用）
    private static HttpClient httpClient;

    // 订阅状态
    private static final AtomicBoolean subscribed = new AtomicBoolean(false);
    private static volatile boolean sseConnected = false;

    // 线程池
    private static ExecutorService sseExecutor;
    private static ScheduledExecutorService fallbackScheduler;

    // 已订阅的服务列表
    private static volatile List<String> subscribedServices;

    // 兜底刷新间隔（5分钟）
    private static final int FALLBACK_INTERVAL_SECONDS = 300;

    static {
        // 初始化 HttpClient
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 初始化并订阅 Mock 规则
     */
    public static void init(String controlPlane, List<String> serviceNames) {
        if (controlPlane != null && !controlPlane.isEmpty()) {
            controlPlaneUrl = controlPlane;
        }
        subscribedServices = serviceNames;

        // 首次拉取所有规则（补偿机制）
        fetchAllRules(serviceNames);

        // 启动 SSE 订阅
        startSseListener();

        // 启动兜底定时刷新
        startFallbackRefresh();

        logger.info("📡 [Mock-SSE] ✅ Mock SSE listener started, services: {}", serviceNames);
    }

    /**
     * 启动 SSE 监听（统一 ProtectionConfigClient 架构）
     */
    private static void startSseListener() {
        if (subscribed.compareAndSet(false, true)) {
            sseExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mock-sse-listener");
                t.setDaemon(true);
                return t;
            });

            sseExecutor.submit(MockRuleSubscriptionClient::connectSse);
        }
    }

    /**
     * 连接 SSE 并监听（HttpClient 异步流方式）
     */
    private static void connectSse() {
        String sseUrl = controlPlaneUrl + "/api/v1/sse/subscribe/all";

        while (subscribed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                logger.info("📡 [Mock-SSE] Connecting to SSE: {}", sseUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build();

                sseConnected = false;

                // 异步发送请求，处理 SSE 流
                httpClient.sendAsync(request, BodyHandlers.ofLines())
                        .thenAccept(response -> {
                            if (response.statusCode() == 200) {
                                logger.info("✅ [Mock-SSE] SSE connection established");
                                sseConnected = true;
                                handleSseStream(response.body());
                            } else {
                                logger.warn("❌ [Mock-SSE] Connection failed with status: {}", response.statusCode());
                            }
                        })
                        .exceptionally(e -> {
                            logger.error("❌ [Mock-SSE] Connection error: {}", e.getMessage());
                            return null;
                        })
                        .join();

            } catch (Exception e) {
                logger.error("❌ [Mock-SSE] SSE listener error: {}", e.getMessage());
            }

            sseConnected = false;
            logger.warn("🔄 [Mock-SSE] Connection lost, reconnecting in 10 seconds...");

            // 断线重连
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 处理 SSE 流（统一格式解析）
     */
    private static void handleSseStream(java.util.stream.Stream<String> lines) {
        StringBuilder eventBuilder = new StringBuilder();

        lines.forEach(line -> {
            try {
                if (line.startsWith("event:")) {
                    // 新事件开始
                    eventBuilder.setLength(0);
                    eventBuilder.append(line.substring(6).trim());
                } else if (line.startsWith("data:")) {
                    // 事件数据
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        handleSseEvent(eventBuilder.toString(), data);
                    }
                } else if (line.isEmpty()) {
                    // 空行表示事件结束
                    eventBuilder.setLength(0);
                }
            } catch (Exception e) {
                logger.error("❌ [Mock-SSE] Error handling SSE line: {}", e.getMessage());
            }
        });
    }

    /**
     * 处理 SSE 事件（统一 config-change 格式）
     *
     * 统一事件格式：
     * {
     *   "type": "mock",
     *   "serviceName": "sample-engine",
     *   "data": { "ruleId": 123, "action": "UPDATE" }
     * }
     */
    @SuppressWarnings("unchecked")
    private static void handleSseEvent(String eventName, String eventData) {
        try {
            if ("config-change".equals(eventName)) {
                // 统一格式处理
                Map<String, Object> event = objectMapper.readValue(eventData, Map.class);
                String type = (String) event.get("type");
                String serviceName = (String) event.get("serviceName");

                // 只处理 mock 类型
                if ("mock".equals(type)) {
                    logger.info("📝 [Mock-SSE] Received config change: service={}, event={}",
                            serviceName, eventData);

                    // 拉取最新规则
                    if (subscribedServices != null && subscribedServices.contains(serviceName)) {
                        fetchRulesForService(serviceName);
                    }
                }
            } else if ("rule-change".equals(eventName)) {
                // 兼容旧格式（过渡期间）
                Map<String, Object> event = objectMapper.readValue(eventData, Map.class);
                String serviceName = (String) event.get("serviceName");

                logger.info("📝 [Mock-SSE] Received rule-change (legacy): service={}", serviceName);

                if (subscribedServices != null && subscribedServices.contains(serviceName)) {
                    fetchRulesForService(serviceName);
                }
            } else if ("connected".equals(eventName)) {
                logger.info("✅ [Mock-SSE] SSE connection confirmed");
            } else if ("heartbeat".equals(eventName)) {
                logger.debug("💓 [Mock-SSE] Heartbeat received");
            }
        } catch (Exception e) {
            logger.error("❌ [Mock-SSE] Failed to handle SSE event: {}", e.getMessage());
        }
    }

    /**
     * 启动兜底定时刷新
     */
    private static void startFallbackRefresh() {
        if (fallbackScheduler != null && !fallbackScheduler.isShutdown()) {
            return;
        }

        fallbackScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-fallback-refresh");
            t.setDaemon(true);
            return t;
        });

        fallbackScheduler.scheduleAtFixedRate(
                () -> {
                    if (subscribedServices != null) {
                        logger.debug("🔄 [Mock-SSE] Fallback refresh triggered");
                        fetchAllRules(subscribedServices);
                    }
                },
                FALLBACK_INTERVAL_SECONDS,
                FALLBACK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        logger.info("📡 [Mock-SSE] Fallback refresh started (interval: {}s)", FALLBACK_INTERVAL_SECONDS);
    }

    /**
     * 从 Control Plane 拉取所有规则
     */
    public static void fetchAllRules(List<String> serviceNames) {
        if (serviceNames == null || serviceNames.isEmpty()) {
            return;
        }

        for (String serviceName : serviceNames) {
            fetchRulesForService(serviceName);
        }
    }

    /**
     * 拉取指定服务的 Mock 规则
     */
    @SuppressWarnings("unchecked")
    public static void fetchRulesForService(String serviceName) {
        String url = controlPlaneUrl + "/api/v1/rules/service/" + serviceName;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rules = objectMapper.readValue(response.body(), List.class);
                MockRuleManager.getInstance().updateRulesFromControlPlane(serviceName, rules);
                logger.info("📋 [Mock-SSE] Rules fetched: service={}, count={}", serviceName, rules.size());
            } else {
                logger.warn("⚠️ [Mock-SSE] Fetch failed: service={}, HTTP {}", serviceName, response.statusCode());
            }
        } catch (Exception e) {
            logger.error("❌ [Mock-SSE] Fetch error: service={}, error={}", serviceName, e.getMessage());
        }
    }

    /**
     * 停止订阅
     */
    public static void shutdown() {
        subscribed.set(false);
        sseConnected = false;

        if (sseExecutor != null) {
            sseExecutor.shutdown();
        }

        if (fallbackScheduler != null) {
            fallbackScheduler.shutdown();
        }

        logger.info("📡 [Mock-SSE] Subscription stopped");
    }

    /**
     * 设置 Control Plane URL
     */
    public static void setControlPlaneUrl(String url) {
        controlPlaneUrl = url;
    }

    /**
     * 获取 Control Plane URL
     */
    public static String getControlPlaneUrl() {
        return controlPlaneUrl;
    }

    /**
     * 检查是否已订阅
     */
    public static boolean isSubscribed() {
        return subscribed.get();
    }

    /**
     * 检查 SSE 连接状态
     */
    public static boolean isSseConnected() {
        return sseConnected;
    }

    /**
     * 获取统计信息
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("subscribed", subscribed.get());
        stats.put("sseConnected", sseConnected);
        stats.put("controlPlaneUrl", controlPlaneUrl);
        stats.put("subscribedServices", subscribedServices);
        return stats;
    }
}
