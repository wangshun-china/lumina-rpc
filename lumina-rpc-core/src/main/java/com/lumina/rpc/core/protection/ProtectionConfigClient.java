package com.lumina.rpc.core.protection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 保护配置客户端
 *
 * 从控制面同步熔断器和限流器配置
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

    /** 定时刷新线程 */
    private ScheduledExecutorService scheduler;

    /** 刷新间隔（秒） */
    private final int refreshIntervalSeconds;

    private ProtectionConfigClient(String controlPlaneUrl, int refreshIntervalSeconds) {
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
     * 启动定时刷新
     */
    public void startRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "protection-config-refresh");
            t.setDaemon(true);
            return t;
        });

        // 首次立即刷新
        refreshConfigs();

        // 定时刷新
        scheduler.scheduleAtFixedRate(
                this::refreshConfigs,
                refreshIntervalSeconds,
                refreshIntervalSeconds,
                TimeUnit.SECONDS
        );

        logger.info("Protection config refresh started (interval: {}s)", refreshIntervalSeconds);
    }

    /**
     * 停止定时刷新
     */
    public void stopRefresh() {
        if (scheduler != null) {
            scheduler.shutdown();
            logger.info("Protection config refresh stopped");
        }
    }

    /**
     * 刷新配置
     */
    public void refreshConfigs() {
        try {
            // 先上报统计数据
            reportAllStats();

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
     * 上报所有服务的统计数据
     */
    private void reportAllStats() {
        // 遍历所有已配置的服务，上报统计数据
        for (String serviceName : configCache.keySet()) {
            try {
                long passed = RateLimiterManager.getInstance().getPassedCount(serviceName);
                long rejected = RateLimiterManager.getInstance().getRejectedCount(serviceName);
                String cbState = CircuitBreakerManager.getInstance().getState(serviceName);

                reportStats(serviceName, passed, rejected, cbState);
            } catch (Exception e) {
                logger.debug("Failed to report stats for {}: {}", serviceName, e.getMessage());
            }
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
     * 上报统计数据到控制面
     */
    public void reportStats(String serviceName, long passed, long rejected, String circuitBreakerState) {
        try {
            String url = controlPlaneUrl + "/api/v1/protection/stats/" + serviceName;

            Map<String, Object> stats = Map.of(
                    "passed", passed,
                    "rejected", rejected,
                    "circuitBreakerState", circuitBreakerState
            );

            String body = objectMapper.writeValueAsString(stats);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            logger.debug("Stats reported for {}: passed={}, rejected={}", serviceName, passed, rejected);
                        }
                    })
                    .exceptionally(e -> {
                        logger.debug("Failed to report stats for {}: {}", serviceName, e.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            logger.debug("Failed to report stats for {}: {}", serviceName, e.getMessage());
        }
    }
}