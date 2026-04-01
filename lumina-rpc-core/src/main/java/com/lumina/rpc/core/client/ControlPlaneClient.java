package com.lumina.rpc.core.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.rpc.core.discovery.ServiceDiscovery;
import com.lumina.rpc.core.discovery.ServiceInstance;
import com.lumina.rpc.core.mock.MockRuleManager;
import com.lumina.rpc.core.protection.ProtectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 统一控制平面客户端（对标 Dubbo RegistryProtocol）
 *
 * 合并以下4个Client的功能：
 * - ServiceRegistryClient: 注册/心跳/注销
 * - ServiceDiscoveryClient: 服务发现/刷新
 * - ProtectionConfigClient: 保护配置同步
 * - MockRuleSubscriptionClient: Mock规则订阅
 *
 * 设计原则（对标 Dubbo）：
 * 1. 单例模式，全局唯一
 * 2. 共享 HttpClient（一个实例）
 * 3. 共享 ScheduledExecutorService（一个线程池）
 * 4. SSE 事件驱动（无轮询兜底、无版本号检查）
 * 5. 断线重连机制
 */
public class ControlPlaneClient {

    private static final Logger logger = LoggerFactory.getLogger(ControlPlaneClient.class);

    // ==================== 单例 ====================
    private static volatile ControlPlaneClient instance;
    private static final Object LOCK = new Object();

    // ==================== 共享资源 ====================
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    // ==================== 配置 ====================
    private final String controlPlaneUrl;

    // ==================== 服务注册状态 ====================
    private volatile boolean registered = false;
    private String serviceName;
    private String host;
    private int port;
    private String version;
    private String instanceId;
    private String serviceMetadata;

    // ==================== SSE状态 ====================
    private volatile boolean sseConnected = false;
    private volatile boolean running = false;
    private volatile List<String> subscribedServices;

    // ==================== 配置缓存 ====================
    private final ConcurrentHashMap<String, ProtectionConfig> protectionConfigCache = new ConcurrentHashMap<>();

    // ==================== 心跳间隔 ====================
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int DISCOVERY_INTERVAL_SECONDS = 30;
    private static final int SSE_RECONNECT_DELAY_SECONDS = 10;

    // ==================== 超时配置 ====================
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    /**
     * 获取单例
     */
    public static ControlPlaneClient getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    throw new IllegalStateException("ControlPlaneClient not initialized. Call initialize() first.");
                }
            }
        }
        return instance;
    }

    /**
     * 初始化（Provider端调用）
     */
    public static synchronized void initialize(String controlPlaneUrl) {
        if (instance == null) {
            instance = new ControlPlaneClient(controlPlaneUrl);
            logger.info("✅ ControlPlaneClient initialized: {}", controlPlaneUrl);
        }
    }

    /**
     * 构造函数
     */
    private ControlPlaneClient(String controlPlaneUrl) {
        this.controlPlaneUrl = controlPlaneUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();

        // 单线程池执行所有定时任务（心跳、发现、SSE重连）
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "control-plane-client");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Provider端：服务注册 ====================

    /**
     * 注册服务实例
     */
    public void register(String serviceName, String host, int port, String version, String metadata) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.version = version != null ? version : "";
        this.serviceMetadata = metadata;
        this.instanceId = serviceName + "@" + host + ":" + port;

        doRegister();
        startHeartbeat();
    }

    /**
     * 执行注册
     */
    private void doRegister() {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/register";

            Map<String, Object> body = new HashMap<>();
            body.put("serviceName", serviceName);
            body.put("instanceId", instanceId);
            body.put("host", host);
            body.put("port", port);
            body.put("version", version);
            body.put("startTime", System.currentTimeMillis());
            if (serviceMetadata != null && !serviceMetadata.isEmpty()) {
                body.put("serviceMetadata", serviceMetadata);
            }

            String json = objectMapper.writeValueAsString(body);
            logger.info("📤 Registering service: {} at {}:{}", serviceName, host, port);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                registered = true;
                logger.info("✅ Service registered: {} at {}:{}", serviceName, host, port);
            } else {
                logger.warn("⚠️ Registration failed: HTTP {}", response.statusCode());
            }

        } catch (Exception e) {
            logger.warn("⚠️ Registration error: {}", e.getMessage());
        }
    }

    /**
     * 启动心跳任务
     */
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!registered) {
                doRegister();
                return;
            }
            sendHeartbeat();
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("💓 Heartbeat started (interval: {}s)", HEARTBEAT_INTERVAL_SECONDS);
    }

    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/heartbeat/" + instanceId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                logger.warn("Instance not found, re-registering: {}", instanceId);
                registered = false;
            } else if (response.statusCode() != 200) {
                logger.warn("Heartbeat failed: HTTP {}", response.statusCode());
            }

        } catch (Exception e) {
            logger.warn("⚠️ Heartbeat error: {}", e.getMessage());
        }
    }

    /**
     * 注销服务
     */
    public void deregister() {
        if (!registered) return;

        try {
            String url = controlPlaneUrl + "/api/v1/registry/deregister/" + instanceId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, BodyHandlers.ofString());
            registered = false;
            logger.info("✅ Service deregistered: {}", instanceId);

        } catch (Exception e) {
            logger.warn("⚠️ Deregister error: {}", e.getMessage());
        }
    }

    // ==================== Consumer端：服务发现 ====================

    /**
     * 启动服务发现
     */
    public void startDiscovery(int refreshIntervalSeconds) {
        // 立即刷新一次
        refreshServices();

        // 定时刷新
        scheduler.scheduleAtFixedRate(
                this::refreshServices,
                refreshIntervalSeconds,
                refreshIntervalSeconds,
                TimeUnit.SECONDS
        );
        logger.info("🔍 Service discovery started (interval: {}s)", refreshIntervalSeconds);
    }

    /**
     * 刷新服务实例
     */
    private void refreshServices() {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/instances";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<ServiceInstance> instances = parseServiceInstances(response.body());

                // 按服务名分组更新
                Map<String, List<ServiceInstance>> grouped = new ConcurrentHashMap<>();
                for (ServiceInstance inst : instances) {
                    grouped.computeIfAbsent(inst.getServiceName(), k -> new ArrayList<>()).add(inst);
                }

                for (Map.Entry<String, List<ServiceInstance>> entry : grouped.entrySet()) {
                    ServiceDiscovery.updateServiceInstances(entry.getKey(), entry.getValue());
                }

                logger.debug("🔍 Refreshed {} instances from control plane", instances.size());
            }

        } catch (Exception e) {
            logger.warn("⚠️ Service discovery error: {}", e.getMessage());
        }
    }

    /**
     * 解析服务实例
     */
    private List<ServiceInstance> parseServiceInstances(String json) {
        try {
            List<Map<String, Object>> data = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<ServiceInstance> instances = new ArrayList<>();
            for (Map<String, Object> item : data) {
                ServiceInstance inst = new ServiceInstance();
                inst.setServiceName((String) item.get("serviceName"));
                inst.setHost((String) item.get("host"));
                inst.setPort((Integer) item.get("port"));
                inst.setVersion((String) item.get("version"));
                inst.setHealthy("UP".equalsIgnoreCase((String) item.getOrDefault("status", "UP")));

                Object startTime = item.get("startTime");
                if (startTime != null) {
                    inst.setStartTime(((Number) startTime).longValue());
                }
                Object warmup = item.get("warmupPeriod");
                if (warmup != null) {
                    inst.setWarmupPeriod(((Number) warmup).longValue());
                }

                instances.add(inst);
            }
            return instances;

        } catch (Exception e) {
            logger.warn("⚠️ Parse service instances error: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== SSE订阅（配置同步 + Mock规则） ====================

    /**
     * 启动SSE订阅（Consumer端）
     */
    public void startSubscription(List<String> serviceNames) {
        this.subscribedServices = serviceNames;
        this.running = true;

        // 首次拉取所有配置
        fetchAllProtectionConfigs();
        fetchAllMockRules(serviceNames);

        // 启动SSE监听
        startSseListener();

        logger.info("📡 SSE subscription started for services: {}", serviceNames);
    }

    /**
     * 启动SSE监听
     */
    private void startSseListener() {
        scheduler.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    connectSse();
                } catch (Exception e) {
                    logger.warn("⚠️ SSE error: {}", e.getMessage());
                }

                sseConnected = false;
                logger.warn("🔄 SSE disconnected, reconnecting in {}s...", SSE_RECONNECT_DELAY_SECONDS);

                try {
                    Thread.sleep(SSE_RECONNECT_DELAY_SECONDS * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * 连接SSE
     */
    private void connectSse() {
        String url = controlPlaneUrl + "/api/v1/sse/subscribe/all";
        logger.info("📡 Connecting to SSE: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        httpClient.sendAsync(request, BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        logger.info("✅ SSE connected");
                        sseConnected = true;
                        handleSseStream(response.body());
                    } else {
                        logger.warn("⚠️ SSE connection failed: HTTP {}", response.statusCode());
                    }
                })
                .exceptionally(e -> {
                    logger.warn("⚠️ SSE error: {}", e.getMessage());
                    return null;
                })
                .join();
    }

    /**
     * 处理SSE流
     */
    private void handleSseStream(java.util.stream.Stream<String> lines) {
        StringBuilder eventBuilder = new StringBuilder();

        lines.forEach(line -> {
            try {
                if (line.startsWith("event:")) {
                    eventBuilder.setLength(0);
                    eventBuilder.append(line.substring(6).trim());
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        handleSseEvent(eventBuilder.toString(), data);
                    }
                } else if (line.isEmpty()) {
                    eventBuilder.setLength(0);
                }
            } catch (Exception e) {
                logger.warn("⚠️ SSE line error: {}", e.getMessage());
            }
        });
    }

    /**
     * 处理SSE事件
     */
    @SuppressWarnings("unchecked")
    private void handleSseEvent(String eventName, String eventData) {
        try {
            if ("config-change".equals(eventName)) {
                Map<String, Object> event = objectMapper.readValue(eventData, Map.class);
                String type = (String) event.get("type");
                String serviceName = (String) event.get("serviceName");

                switch (type) {
                    case "protection" -> {
                        logger.info("📝 Protection config changed: {}", serviceName);
                        fetchProtectionConfig(serviceName);
                    }
                    case "mock" -> {
                        logger.info("📝 Mock rule changed: {}", serviceName);
                        if (subscribedServices != null && subscribedServices.contains(serviceName)) {
                            fetchMockRules(serviceName);
                        }
                    }
                }
            } else if ("heartbeat".equals(eventName)) {
                logger.debug("💓 SSE heartbeat");
            }
        } catch (Exception e) {
            logger.warn("⚠️ SSE event error: {}", e.getMessage());
        }
    }

    // ==================== 保护配置 ====================

    /**
     * 拉取所有保护配置
     */
    private void fetchAllProtectionConfigs() {
        try {
            String url = controlPlaneUrl + "/api/v1/protection/configs";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> configs = (List<Map<String, Object>>) result.get("configs");

                for (Map<String, Object> configMap : configs) {
                    ProtectionConfig config = parseProtectionConfig(configMap);
                    protectionConfigCache.put(config.getServiceName(), config);
                }

                logger.info("📋 Loaded {} protection configs", configs.size());
            }

        } catch (Exception e) {
            logger.warn("⚠️ Fetch protection configs error: {}", e.getMessage());
        }
    }

    /**
     * 拉取单个服务保护配置
     */
    private void fetchProtectionConfig(String serviceName) {
        try {
            String url = controlPlaneUrl + "/api/v1/protection/configs/" + serviceName;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> configMap = objectMapper.readValue(response.body(), Map.class);
                ProtectionConfig config = parseProtectionConfig(configMap);
                protectionConfigCache.put(serviceName, config);
                logger.info("📋 Updated protection config: {}", serviceName);
            }

        } catch (Exception e) {
            logger.warn("⚠️ Fetch protection config error: {}", e.getMessage());
        }
    }

    /**
     * 获取保护配置
     */
    public ProtectionConfig getProtectionConfig(String serviceName) {
        ProtectionConfig config = protectionConfigCache.get(serviceName);
        if (config == null) {
            config = new ProtectionConfig(serviceName);
        }
        return config;
    }

    /**
     * 解析保护配置
     */
    private ProtectionConfig parseProtectionConfig(Map<String, Object> map) {
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

    // ==================== Mock规则 ====================

    /**
     * 拉取所有Mock规则
     */
    private void fetchAllMockRules(List<String> serviceNames) {
        for (String serviceName : serviceNames) {
            fetchMockRules(serviceName);
        }
    }

    /**
     * 拉取单个服务Mock规则
     */
    @SuppressWarnings("unchecked")
    private void fetchMockRules(String serviceName) {
        try {
            String url = controlPlaneUrl + "/api/v1/rules/service/" + serviceName;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> rules = objectMapper.readValue(response.body(), List.class);
                MockRuleManager.getInstance().updateRulesFromControlPlane(serviceName, rules);
                logger.info("📋 Updated mock rules: {} (count: {})", serviceName, rules.size());
            }

        } catch (Exception e) {
            logger.warn("⚠️ Fetch mock rules error: {}", e.getMessage());
        }
    }

    // ==================== 优雅停机 ====================

    /**
     * 停止客户端
     */
    public void shutdown() {
        logger.info("🛑 Shutting down ControlPlaneClient...");

        running = false;
        sseConnected = false;

        // 注销服务
        deregister();

        // 停止线程池
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("✅ ControlPlaneClient shutdown complete");
    }

    // ==================== 状态查询 ====================

    public boolean isRegistered() {
        return registered;
    }

    public boolean isSseConnected() {
        return sseConnected;
    }

    public String getControlPlaneUrl() {
        return controlPlaneUrl;
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("registered", registered);
        stats.put("sseConnected", sseConnected);
        stats.put("controlPlaneUrl", controlPlaneUrl);
        stats.put("protectionConfigCacheSize", protectionConfigCache.size());
        return stats;
    }

    /**
     * 重置单例（测试用）
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
}