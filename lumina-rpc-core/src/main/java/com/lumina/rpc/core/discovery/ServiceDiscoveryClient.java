package com.lumina.rpc.core.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 服务发现客户端
 *
 * 从控制平面拉取服务实例列表并更新本地缓存
 * 支持定时刷新机制
 */
public class ServiceDiscoveryClient {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryClient.class);

    // 超时配置（秒）
    private static final int CONNECT_TIMEOUT = 10;
    private static final int REQUEST_TIMEOUT = 15;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
            .build();

    // 控制平面基础 URL
    private static String controlPlaneUrl = "http://localhost:8080";

    // 定时刷新任务
    private static ScheduledExecutorService scheduler;

    // 是否正在运行
    private static volatile boolean running = false;

    /**
     * 初始化服务发现客户端
     *
     * @param controlPlaneUrl 控制平面 URL
     * @param refreshInterval 刷新间隔（秒）
     */
    public static void init(String controlPlaneUrl, int refreshInterval) {
        if (controlPlaneUrl != null && !controlPlaneUrl.isEmpty()) {
            ServiceDiscoveryClient.controlPlaneUrl = controlPlaneUrl;
        }

        // 立即拉取一次所有服务实例
        refreshAllServices();

        // 启动定时刷新任务
        if (!running) {
            running = true;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "service-discovery-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                    ServiceDiscoveryClient::refreshAllServices,
                    refreshInterval,
                    refreshInterval,
                    TimeUnit.SECONDS
            );
            logger.info("Service discovery client started, refresh interval: {}s", refreshInterval);
        }
    }

    // 本地缓存保留策略：AP 架构高可用原则
    private static List<ServiceInstance> lastSuccessfulInstances = new ArrayList<>();
    private static volatile long lastSuccessfulFetchTime = 0;

    /**
     * 刷新所有服务实例（高可用加固版）
     *
     * AP 架构原则：当控制面不可用时，保留上一次成功拉取的缓存数据
     * 不因为一次拉取失败就清空本地路由表！
     */
    public static void refreshAllServices() {
        try {
            List<ServiceInstance> instances = fetchAllInstances();

            // 高可用策略：如果拉取为空，保留上一次缓存，不打错误日志
            if (instances == null || instances.isEmpty()) {
                long secondsSinceLastSuccess = (System.currentTimeMillis() - lastSuccessfulFetchTime) / 1000;
                logger.warn("⚠️ [HA-Discovery] No service instances fetched from control plane. " +
                        "Keeping {} cached instances from {} seconds ago. " +
                        "This is normal during network jitter or control plane restart.",
                        lastSuccessfulInstances.size(), secondsSinceLastSuccess);
                return;
            }

            // 按服务名分组
            java.util.Map<String, List<ServiceInstance>> grouped = new java.util.concurrent.ConcurrentHashMap<>();
            for (ServiceInstance instance : instances) {
                grouped.computeIfAbsent(instance.getServiceName(), k -> new ArrayList<>()).add(instance);
            }

            // 更新本地缓存
            for (java.util.Map.Entry<String, List<ServiceInstance>> entry : grouped.entrySet()) {
                ServiceDiscovery.updateServiceInstances(entry.getKey(), entry.getValue());
            }

            // 记录成功状态
            lastSuccessfulInstances = new ArrayList<>(instances);
            lastSuccessfulFetchTime = System.currentTimeMillis();

            logger.info("✅ [HA-Discovery] Refreshed {} services ({} instances) from control plane",
                    grouped.size(), instances.size());

        } catch (Exception e) {
            // 高可用：任何异常都不应该清空缓存！
            long secondsSinceLastSuccess = (System.currentTimeMillis() - lastSuccessfulFetchTime) / 1000;
            logger.error("❌ [HA-Discovery] Failed to refresh services: {}. " +
                    "Keeping {} cached instances from {} seconds ago. " +
                    "This is an AP architecture - availability over consistency.",
                    e.getMessage(), lastSuccessfulInstances.size(), secondsSinceLastSuccess, e);
        }
    }

    /**
     * 刷新指定服务
     *
     * @param serviceName 服务名称
     */
    public static void refreshService(String serviceName) {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/instances/" + serviceName;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<ServiceInstance> instances = parseResponse(response.body());
                ServiceDiscovery.updateServiceInstances(serviceName, instances);
                logger.info("Refreshed service {}: {} instances", serviceName, instances.size());
            } else {
                logger.warn("Failed to refresh service {}: HTTP {}", serviceName, response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Failed to refresh service: {}", serviceName, e);
        }
    }

    /**
     * 从控制平面获取所有服务实例
     *
     * @return 服务实例列表
     */
    private static List<ServiceInstance> fetchAllInstances() {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/instances";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                logger.warn("Failed to fetch instances: HTTP {}", response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Failed to fetch service instances from control plane", e);
        }

        return new ArrayList<>();
    }

    /**
     * 解析响应
     */
    @SuppressWarnings("unchecked")
    private static List<ServiceInstance> parseResponse(String json) {
        try {
            List<java.util.Map<String, Object>> data = objectMapper.readValue(json,
                    new TypeReference<List<java.util.Map<String, Object>>>() {});

            List<ServiceInstance> instances = new ArrayList<>();
            for (java.util.Map<String, Object> item : data) {
                ServiceInstance instance = new ServiceInstance();
                instance.setServiceName((String) item.get("serviceName"));
                instance.setHost((String) item.get("host"));
                instance.setPort((Integer) item.get("port"));
                instance.setVersion((String) item.get("version"));

                // 解析健康状态：status = "UP" 表示健康
                String status = (String) item.getOrDefault("status", "UP");
                instance.setHealthy("UP".equalsIgnoreCase(status));

                instances.add(instance);
            }

            return instances;

        } catch (Exception e) {
            logger.error("Failed to parse service instances response", e);
            return new ArrayList<>();
        }
    }

    /**
     * 停止服务发现客户端
     */
    public static void shutdown() {
        logger.info("🛑 [Graceful Shutdown] Stopping service discovery client...");

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        running = false;
        logger.info("✅ [Graceful Shutdown] Service discovery client stopped");
    }

    /**
     * 设置控制平面 URL
     */
    public static void setControlPlaneUrl(String url) {
        controlPlaneUrl = url;
    }

    /**
     * 获取控制平面 URL
     */
    public static String getControlPlaneUrl() {
        return controlPlaneUrl;
    }
}