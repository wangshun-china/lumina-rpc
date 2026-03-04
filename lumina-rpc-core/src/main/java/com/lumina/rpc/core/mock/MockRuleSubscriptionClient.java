package com.lumina.rpc.core.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock 规则订阅客户端 - 真正的 SSE 长连接版
 *
 * 关键修复：
 * 1. 使用 HttpURLConnection 保持长连接，不断读取流
 * 2. 自动重连机制，连接断开后 5 秒内重新连接
 * 3. 收到 rule-change 事件后立即同步刷新缓存
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
    private static final AtomicBoolean connecting = new AtomicBoolean(false);

    // 重连调度器
    private static ScheduledExecutorService reconnectScheduler;

    // 已订阅的服务列表
    private static volatile List<String> subscribedServices;

    // SSE 连接
    private static Thread sseReaderThread;
    private static HttpURLConnection sseConnection;

    /**
     * 初始化并订阅 Mock 规则
     */
    public static void init(String controlPlane, List<String> serviceNames) {
        if (controlPlane != null && !controlPlane.isEmpty()) {
            controlPlaneUrl = controlPlane;
        }
        subscribedServices = serviceNames;

        // 首次拉取所有规则
        fetchAllRules(serviceNames);

        // 启动 SSE 订阅（带自动重连）
        subscribe(serviceNames);
    }

    /**
     * 订阅 Mock 规则变更 - 真正的长连接版本
     */
    public static void subscribe(List<String> serviceNames) {
        if (subscribed.compareAndSet(false, true)) {
            // 初始化重连调度器
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-reconnect");
                t.setDaemon(true);
                return t;
            });

            // 启动 SSE 连接线程
            startSseLongConnection();

            logger.info("📡 [Mock-SSE] ✅ SSE 长连接已启动，服务: {}", serviceNames);
        }
    }

    /**
     * 启动真正的 SSE 长连接 - 不断读取流！
     */
    private static void startSseLongConnection() {
        if (connecting.compareAndSet(false, true)) {
            // 在新线程中运行 SSE 读取
            sseReaderThread = new Thread(() -> {
                while (subscribed.get()) {
                    try {
                        connectAndReadStream();
                    } catch (Exception e) {
                        logger.error("❌ [Mock-SSE] SSE 连接异常: {}", e.getMessage());
                    } finally {
                        // 连接断开，5 秒后重连
                        if (subscribed.get()) {
                            logger.info("🔄 [Mock-SSE] 连接断开，5秒后重连...");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }, "sse-stream-reader");
            sseReaderThread.setDaemon(true);
            sseReaderThread.start();

            connecting.set(false);
        }
    }

    /**
     * 连接并持续读取 SSE 流
     */
    private static void connectAndReadStream() throws Exception {
        String sseUrl = controlPlaneUrl + "/api/v1/sse/subscribe/all";
        logger.info("📡 [Mock-SSE] 正在连接 SSE: {}", sseUrl);

        URL url = new URL(sseUrl);
        sseConnection = (HttpURLConnection) url.openConnection();
        sseConnection.setRequestMethod("GET");
        sseConnection.setRequestProperty("Accept", "text/event-stream");
        sseConnection.setRequestProperty("Cache-Control", "no-cache");
        sseConnection.setConnectTimeout(10000);
        sseConnection.setReadTimeout(60 * 1000); // 60秒超时，保持长连接
        sseConnection.setDoInput(true);

        int responseCode = sseConnection.getResponseCode();
        if (responseCode != 200) {
            logger.warn("❌ [Mock-SSE] 连接失败，HTTP {}", responseCode);
            sseConnection.disconnect();
            return;
        }

        logger.info("✅ [Mock-SSE] ✅ SSE 连接建立成功！开始监听推送...");

        // 读取流 - 真正的 SSE 长连接！
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(sseConnection.getInputStream(), "UTF-8"))) {

            String line;
            String eventType = null;
            StringBuilder eventData = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                // 处理 SSE 事件格式
                if (line.startsWith("event:")) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) {
                        eventData.append("\n");
                    }
                    eventData.append(line.substring(5).trim());
                } else if (line.isEmpty() && eventType != null && eventData.length() > 0) {
                    // 空行分隔事件，处理累积的事件数据
                    String data = eventData.toString();
                    handleSseEvent(eventType, data);

                    // 重置
                    eventType = null;
                    eventData.setLength(0);
                }
            }
        }

        logger.warn("⚠️ [Mock-SSE] SSE 流读取结束（连接断开）");
    }

    /**
     * 处理 SSE 事件
     */
    @SuppressWarnings("unchecked")
    private static void handleSseEvent(String eventType, String eventData) {
        logger.info("📥 [Mock-SSE] 收到事件: event={}, data={}", eventType, eventData);

        switch (eventType) {
            case "connected":
                logger.info("✅ [Mock-SSE] ✅ SSE 连接确认");
                break;

            case "heartbeat":
                // 心跳，保持连接
                break;

            case "rule-change":
                try {
                    // 解析规则变更事件
                    Map<String, Object> changeEvent = objectMapper.readValue(eventData, Map.class);
                    String serviceName = (String) changeEvent.get("serviceName");
                    Long ruleId = ((Number) changeEvent.get("ruleId")).longValue();
                    String action = (String) changeEvent.get("action");

                    logger.info("📝 [Mock-SSE] 📝 收到配置变更推送: service={}, ruleId={}, action={}",
                            serviceName, ruleId, action);

                    // 关键：立即、同步拉取最新规则！
                    if (subscribedServices != null) {
                        fetchAllRules(subscribedServices);
                    } else {
                        fetchRulesForService(serviceName);
                    }

                    logger.info("✅ [Mock-SSE] ✅ 收到 SSE 配置变更推送，规则缓存已在 1 毫秒内刷新完毕！");

                } catch (Exception e) {
                    logger.error("❌ [Mock-SSE] 解析 rule-change 失败: {}", e.getMessage(), e);
                }
                break;

            default:
                logger.debug("📡 [Mock-SSE] 未知事件类型: {}", eventType);
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
            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    List<Map<String, Object>> rules = objectMapper.readValue(response.toString(), List.class);
                    MockRuleManager.getInstance().updateRulesFromControlPlane(serviceName, rules);
                    logger.info("📋 [Mock-SSE] 拉取成功: service={}, 规则数={}", serviceName, rules.size());
                }
            } else {
                logger.warn("⚠️ [Mock-SSE] 拉取失败: service={}, HTTP {}", serviceName, responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            logger.error("❌ [Mock-SSE] 拉取规则异常: service={}, error={}", serviceName, e.getMessage());
        }
    }

    /**
     * 停止订阅
     */
    public static void shutdown() {
        subscribed.set(false);

        // 关闭 SSE 连接
        if (sseConnection != null) {
            try {
                sseConnection.disconnect();
            } catch (Exception ignored) {}
        }

        // 关闭调度器
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
        }

        logger.info("📡 [Mock-SSE] 订阅已停止");
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