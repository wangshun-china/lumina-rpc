package com.lumina.rpc.core.protection;

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

/**
 * 保护配置客户端
 *
 * 从控制面同步熔断器和限流器配置
 * 支持SSE实时推送 + 定时轮询兜底
 */
public class ProtectionConfigClient {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigClient.class);

    private static volatile ProtectionConfigClient instance;

    private final String controlPlaneUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 配置缓存 */
    private final ConcurrentHashMap<String, ProtectionConfig> configCache = new ConcurrentHashMap<>();

    /** 本地配置版本号 */
    private volatile long localVersion = 0;

    /** SSE监听线程 */
    private ExecutorService sseExecutor;

    /** 定时刷新线程（兜底） */
    private ScheduledExecutorService scheduler;

    /** 刷新间隔（秒） */
    private final int refreshIntervalSeconds;

    /** SSE连接是否活跃 */
    private volatile boolean sseConnected = false;

    public ProtectionConfigClient(String controlPlaneUrl, int refreshIntervalSeconds) {
        this.controlPlaneUrl = controlPlaneUrl;
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static ProtectionConfigClient getInstance() {
        if (instance == null) {
            synchronized (ProtectionConfigClient.class) {
                if (instance == null) {
                    throw new IllegalStateException("ProtectionConfigClient not initialized. Call initialize() first.");
                }
            }
        }
        return instance;
    }

    /**
     * 初始化客户端
     */
    public static synchronized void initialize(String controlPlaneUrl, int refreshIntervalSeconds) {
        if (instance == null) {
            instance = new ProtectionConfigClient(controlPlaneUrl, refreshIntervalSeconds);
            logger.info("ProtectionConfigClient initialized with control plane: {}", controlPlaneUrl);
        }
    }

    /**
     * 启动配置监听（SSE + 轮询兜底）
     */
    public void startRefresh() {
        // 首次立即刷新（补偿机制：防止SSE漏消息）
        refreshConfigs();

        // 启动SSE实时监听
        startSseListener();

        // 启动定时轮询兜底（每5分钟一次，频率很低）
        startPeriodicRefresh();

        logger.info("Protection config listener started (SSE + periodic fallback)");
    }

    /**
     * 启动SSE实时监听
     */
    private void startSseListener() {
        if (sseExecutor != null && !sseExecutor.isShutdown()) {
            return;
        }

        sseExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-config-listener");
            t.setDaemon(true);
            return t;
        });

        sseExecutor.submit(this::connectSse);
    }

    /**
     * 连接SSE并监听配置变更
     */
    private void connectSse() {
        String sseUrl = controlPlaneUrl + "/api/v1/sse/subscribe/all";

        while (!Thread.currentThread().isInterrupted()) {
            try {
                logger.info("Connecting to SSE endpoint: {}", sseUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", "text/event-stream")
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build();

                sseConnected = false;

                httpClient.sendAsync(request, BodyHandlers.ofLines())
                        .thenAccept(response -> {
                            if (response.statusCode() == 200) {
                                logger.info("SSE connection established");
                                sseConnected = true;
                                handleSseStream(response.body());
                            } else {
                                logger.warn("SSE connection failed with status: {}", response.statusCode());
                            }
                        })
                        .exceptionally(e -> {
                            logger.error("SSE connection error: {}", e.getMessage());
                            return null;
                        })
                        .join();

            } catch (Exception e) {
                logger.error("SSE listener error: {}", e.getMessage());
            }

            sseConnected = false;
            logger.warn("SSE connection lost, reconnecting in 10 seconds...");

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
     * 处理SSE流
     */
    private void handleSseStream(java.util.stream.Stream<String> lines) {
        StringBuilder eventBuilder = new StringBuilder();

        lines.forEach(line -> {
            try {
                if (line.startsWith("event:")) {
                    // 新事件开始，清空之前的数据
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
                logger.error("Error handling SSE line: {}", e.getMessage());
            }
        });
    }

    /**
     * 处理SSE事件
     */
    private void handleSseEvent(String eventName, String data) {
        try {
            if ("config-change".equals(eventName)) {
                Map<String, Object> event = objectMapper.readValue(data, Map.class);
                String type = (String) event.get("type");
                String serviceName = (String) event.get("serviceName");

                logger.info("Received config change event: type={}, service={}", type, serviceName);

                if ("protection".equals(type)) {
                    // 解析并更新保护配置
                    Map<String, Object> configData = (Map<String, Object>) event.get("data");
                    ProtectionConfig config = parseConfig(configData);
                    configCache.put(serviceName, config);
                    localVersion = System.currentTimeMillis();
                    logger.info("Updated protection config for service: {}", serviceName);
                }
            } else if ("heartbeat".equals(eventName)) {
                logger.debug("Received SSE heartbeat");
            }
        } catch (Exception e) {
            logger.error("Failed to handle SSE event: {}", e.getMessage());
        }
    }

    /**
     * 启动定时轮询兜底
     */
    private void startPeriodicRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "protection-config-refresh");
            t.setDaemon(true);
            return t;
        });

        // 每5分钟轮询一次（兜底，频率很低）
        scheduler.scheduleAtFixedRate(
                this::refreshConfigs,
                300,  // 5分钟
                300,  // 5分钟
                TimeUnit.SECONDS
        );

        logger.info("Periodic refresh started (interval: 300s, SSE connected: {})", sseConnected);
    }

    /**
     * 停止所有监听
     */
    public void stopRefresh() {
        // 停止SSE
        if (sseExecutor != null) {
            sseExecutor.shutdown();
            logger.info("SSE listener stopped");
        }

        // 停止定时刷新
        if (scheduler != null) {
            scheduler.shutdown();
            logger.info("Protection config refresh stopped");
        }

        sseConnected = false;
    }

    /**
     * 检查SSE连接状态
     */
    public boolean isSseConnected() {
        return sseConnected;
    }

    /**
     * 刷新配置
     */
    public void refreshConfigs() {
        try {
            // 1. 检查版本号
            long remoteVersion = fetchRemoteVersion();
            if (remoteVersion <= localVersion && localVersion > 0) {
                logger.debug("Protection config unchanged (local: {}, remote: {})", localVersion, remoteVersion);
                return;
            }

            // 2. 拉取所有配置
            fetchAllConfigs();

            localVersion = remoteVersion;
            logger.info("Protection configs refreshed (version: {})", localVersion);

        } catch (Exception e) {
            logger.warn("Failed to refresh protection configs: {}", e.getMessage());
        }
    }

    /**
     * 获取远程版本号
     */
    private long fetchRemoteVersion() throws Exception {
        String url = controlPlaneUrl + "/api/v1/protection/version";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            return ((Number) result.get("version")).longValue();
        }

        return 0;
    }

    /**
     * 拉取所有配置
     */
    private void fetchAllConfigs() throws Exception {
        String url = controlPlaneUrl + "/api/v1/protection/configs";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> configs = (List<Map<String, Object>>) result.get("configs");

            configCache.clear();

            for (Map<String, Object> configMap : configs) {
                ProtectionConfig config = parseConfig(configMap);
                configCache.put(config.getServiceName(), config);
            }

            logger.info("Loaded {} protection configs from control plane", configs.size());
        }
    }

    /**
     * 获取指定服务的配置
     */
    public ProtectionConfig getConfig(String serviceName) {
        ProtectionConfig config = configCache.get(serviceName);

        if (config == null) {
            // 返回默认配置
            config = new ProtectionConfig(serviceName);
        }
        return config;
    }

    /**
     * 批量获取配置
     */
    public Map<String, ProtectionConfig> getConfigs(List<String> serviceNames) {
        Map<String, ProtectionConfig> result = new ConcurrentHashMap<>();
        for (String serviceName : serviceNames) {
            result.put(serviceName, getConfig(serviceName));
        }
        return result;
    }

    /**
     * 解析配置
     */
    private ProtectionConfig parseConfig(Map<String, Object> map) {
        ProtectionConfig config = new ProtectionConfig();
        config.setServiceName((String) map.get("serviceName"));
        config.setCircuitBreakerEnabled((Boolean) map.getOrDefault("circuitBreakerEnabled", true));
        config.setCircuitBreakerThreshold(((Number) map.getOrDefault("circuitBreakerThreshold", 50)).intValue());
        config.setCircuitBreakerTimeout(((Number) map.getOrDefault("circuitBreakerTimeout", 30000)).longValue());
        config.setRateLimiterEnabled((Boolean) map.getOrDefault("rateLimiterEnabled", false));
        config.setRateLimiterPermits(((Number) map.getOrDefault("rateLimiterPermits", 100)).intValue());
        config.setClusterStrategy((String) map.getOrDefault("clusterStrategy", "failover"));
        config.setRetries(((Number) map.getOrDefault("retries", 3)).intValue());
        config.setTimeout(((Number) map.getOrDefault("timeoutMs", 0)).longValue());
        return config;
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return configCache.size();
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        configCache.clear();
        localVersion = 0;
    }

    /**
     * 重置单例（用于测试）
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.stopRefresh();
            instance.clearCache();
            instance = null;
        }
    }

    /**
     * 获取监听器统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("sseConnected", sseConnected);
        stats.put("cacheSize", configCache.size());
        stats.put("localVersion", localVersion);
        return stats;
    }
}