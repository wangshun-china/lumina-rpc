package com.lumina.rpc.core.shutdown;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 优雅停机配置客户端
 *
 * Provider 端使用，与控制平面同步停机配置：
 * 1. 定时上报活跃请求数
 * 2. 获取停机信号
 * 3. 同步停机超时时间
 */
public class ShutdownConfigClient {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownConfigClient.class);

    private static final String CONTROL_PLANE_URL = "http://127.0.0.1:8080";

    private static ShutdownConfigClient instance;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ShutdownConfig> configRef;

    private String serviceName;
    private volatile boolean started = false;

    /** 停机配置 */
    public static class ShutdownConfig {
        public boolean shuttingDown;
        public long timeoutMs;
        public boolean enabled;

        public ShutdownConfig(boolean shuttingDown, long timeoutMs, boolean enabled) {
            this.shuttingDown = shuttingDown;
            this.timeoutMs = timeoutMs;
            this.enabled = enabled;
        }
    }

    private ShutdownConfigClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shutdown-config-sync");
            t.setDaemon(true);
            return t;
        });
        this.configRef = new AtomicReference<>(new ShutdownConfig(false, 10000, true));
    }

    public static synchronized ShutdownConfigClient getInstance() {
        if (instance == null) {
            instance = new ShutdownConfigClient();
        }
        return instance;
    }

    /**
     * 启动配置同步
     */
    public void start(String serviceName) {
        if (started) {
            return;
        }

        this.serviceName = serviceName;
        this.started = true;

        // 每 2 秒同步一次
        scheduler.scheduleAtFixedRate(this::syncWithControlPlane, 0, 2, TimeUnit.SECONDS);

        logger.info("🔄 [ShutdownConfigClient] Started syncing with Control Plane for service: {}", serviceName);
    }

    /**
     * 与控制平面同步
     */
    private void syncWithControlPlane() {
        if (serviceName == null) {
            return;
        }

        try {
            // 上报活跃请求数
            int activeRequests = GracefulShutdownManager.getInstance().getActiveRequestCount();

            Map<String, Object> body = Map.of("activeRequests", activeRequests);
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CONTROL_PLANE_URL + "/api/v1/shutdown/configs/"
                            + URLEncoder.encode(serviceName) + "/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

                // 更新本地配置
                boolean shuttingDown = Boolean.TRUE.equals(result.get("shuttingDown"));
                long timeoutMs = result.get("timeoutMs") != null
                        ? ((Number) result.get("timeoutMs")).longValue()
                        : 10000L;
                boolean enabled = !Boolean.FALSE.equals(result.get("enabled"));

                ShutdownConfig newConfig = new ShutdownConfig(shuttingDown, timeoutMs, enabled);
                ShutdownConfig oldConfig = configRef.getAndSet(newConfig);

                // 检测停机信号变化
                if (shuttingDown && (oldConfig == null || !oldConfig.shuttingDown)) {
                    logger.warn("🛑 [ShutdownConfigClient] Received shutdown signal from Control Plane!");
                    // 更新 GracefulShutdownManager 的超时时间
                    GracefulShutdownManager.getInstance().setShutdownTimeout(timeoutMs);
                    // 触发停机
                    GracefulShutdownManager.getInstance().gracefulShutdown();
                }

                // 同步超时时间
                if (timeoutMs != (oldConfig != null ? oldConfig.timeoutMs : 0)) {
                    GracefulShutdownManager.getInstance().setShutdownTimeout(timeoutMs);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to sync with Control Plane: {}", e.getMessage());
        }
    }

    /**
     * 获取当前配置
     */
    public ShutdownConfig getConfig() {
        return configRef.get();
    }

    /**
     * 检查是否应该停机
     */
    public boolean shouldShutdown() {
        return configRef.get().shuttingDown;
    }

    /**
     * 停止同步
     */
    public void stop() {
        if (!started) {
            return;
        }

        scheduler.shutdown();
        started = false;
        logger.info("🛑 [ShutdownConfigClient] Stopped");
    }

    /**
     * URL 编码辅助
     */
    private static class URLEncoder {
        static String encode(String s) {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}