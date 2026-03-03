package com.lumina.rpc.core.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock 规则订阅客户端
 *
 * 通过 SSE (Server-Sent Events) 订阅 Control Plane 的 Mock 规则变更
 * 当规则变更时，自动更新本地 MockRuleManager 缓存
 *
 * 使用方式：
 * 1. Consumer 启动时调用 MockRuleSubscriptionClient.subscribe(serviceName)
 * 2. 规则变更会自动同步到 MockRuleManager
 * 3. RPC 调用时自动命中 Mock 规则，短路网络请求
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
public class MockRuleSubscriptionClient {

    private static final Logger logger = LoggerFactory.getLogger(MockRuleSubscriptionClient.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Control Plane 地址
    private static String controlPlaneUrl = "http://localhost:8080";

    // 订阅状态
    private static final AtomicBoolean subscribed = new AtomicBoolean(false);

    // HTTP 客户端
    private static HttpClient httpClient;

    // 重连调度器
    private static ScheduledExecutorService reconnectScheduler;

    // 已订阅的服务列表
    private static volatile List<String> subscribedServices;

    static {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 初始化并订阅 Mock 规则
     *
     * @param controlPlane Control Plane 地址
     * @param serviceNames 需要订阅的服务名称列表
     */
    public static void init(String controlPlane, List<String> serviceNames) {
        if (controlPlane != null && !controlPlane.isEmpty()) {
            controlPlaneUrl = controlPlane;
        }
        subscribedServices = serviceNames;

        // 首次拉取所有规则
        fetchAllRules(serviceNames);

        // 启动 SSE 订阅
        subscribe(serviceNames);
    }

    /**
     * 订阅 Mock 规则变更
     *
     * @param serviceNames 服务名称列表
     */
    public static void subscribe(List<String> serviceNames) {
        if (subscribed.compareAndSet(false, true)) {
            // 启动后台线程监听 SSE
            Thread sseThread = new Thread(() -> {
                while (subscribed.get()) {
                    try {
                        startSseConnection(serviceNames);
                    } catch (Exception e) {
                        logger.warn("SSE connection failed, will retry in 5 seconds: {}", e.getMessage());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "mock-rule-sse-client");
            sseThread.setDaemon(true);
            sseThread.start();

            logger.info("📡 [Mock-SSE] Started SSE subscription for services: {}", serviceNames);
        }
    }

    /**
     * 建立 SSE 连接
     */
    @SuppressWarnings("unchecked")
    private static void startSseConnection(List<String> serviceNames) {
        // 订阅所有服务的规则变更
        String sseUrl = controlPlaneUrl + "/api/v1/sse/subscribe/all";

        logger.info("📡 [Mock-SSE] Connecting to SSE endpoint: {}", sseUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .timeout(Duration.ofMinutes(30))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("✅ [Mock-SSE] Connected to SSE stream");

                // 解析 SSE 事件流
                String body = response.body();
                parseSseEvents(body);
            } else {
                logger.warn("SSE connection returned status: {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("SSE connection error: {}", e.getMessage());
            throw new RuntimeException("SSE connection failed", e);
        }
    }

    /**
     * 解析 SSE 事件
     */
    @SuppressWarnings("unchecked")
    private static void parseSseEvents(String sseData) {
        if (sseData == null || sseData.isEmpty()) {
            return;
        }

        String[] lines = sseData.split("\n");
        String eventType = null;
        String eventData = null;

        for (String line : lines) {
            if (line.startsWith("event:")) {
                eventType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                eventData = line.substring(5).trim();
            } else if (line.isEmpty() && eventType != null && eventData != null) {
                // 事件结束，处理事件
                handleSseEvent(eventType, eventData);
                eventType = null;
                eventData = null;
            }
        }
    }

    /**
     * 处理 SSE 事件
     */
    @SuppressWarnings("unchecked")
    private static void handleSseEvent(String eventType, String eventData) {
        logger.debug("📡 [Mock-SSE] Received event: {} - {}", eventType, eventData);

        switch (eventType) {
            case "connected":
                logger.info("📡 [Mock-SSE] SSE connection established");
                break;

            case "heartbeat":
                // 心跳，忽略
                break;

            case "rule-change":
                try {
                    // 解析规则变更事件
                    Map<String, Object> changeEvent = objectMapper.readValue(eventData, Map.class);
                    String serviceName = (String) changeEvent.get("serviceName");
                    Long ruleId = ((Number) changeEvent.get("ruleId")).longValue();
                    String action = (String) changeEvent.get("action");

                    logger.info("📝 [Mock-SSE] Rule change event: service={}, ruleId={}, action={}",
                            serviceName, ruleId, action);

                    // 重新拉取该服务的规则
                    fetchRulesForService(serviceName);

                } catch (Exception e) {
                    logger.warn("Failed to parse rule-change event: {}", e.getMessage());
                }
                break;

            default:
                logger.debug("Unknown SSE event type: {}", eventType);
        }
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
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rules = objectMapper.readValue(response.body(), List.class);
                MockRuleManager.getInstance().updateRulesFromControlPlane(serviceName, rules);
                logger.info("📋 [Mock-SSE] Fetched {} rules for service: {}",
                        rules != null ? rules.size() : 0, serviceName);
            } else {
                logger.warn("Failed to fetch rules for service {}: HTTP {}", serviceName, response.statusCode());
            }
        } catch (Exception e) {
            logger.warn("Error fetching rules for service {}: {}", serviceName, e.getMessage());
        }
    }

    /**
     * 停止订阅
     */
    public static void shutdown() {
        subscribed.set(false);
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
}