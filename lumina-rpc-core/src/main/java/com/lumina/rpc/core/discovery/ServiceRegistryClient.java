package com.lumina.rpc.core.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 服务注册客户端
 *
 * Provider 端使用此客户端向控制平面注册服务实例
 * 并定期发送心跳维持服务实例健康状态
 *
 * 支持服务元数据上报（接口方法信息）
 *
 * 防御性编程特性：
 * 1. 注册失败不阻断启动（只打警告日志）
 * 2. 支持重试机制
 * 3. 详细日志输出便于排查问题
 */
public class ServiceRegistryClient {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistryClient.class);

    // 超时配置（秒）- 高可用优化：快速失败，防止阻塞
    private static final int CONNECT_TIMEOUT = 5;   // 连接超时 5 秒
    private static final int REQUEST_TIMEOUT = 10;  // 读取超时 10 秒

    // 注册重试配置
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_SECONDS = 5;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
            .build();

    // 控制平面基础 URL
    private static String controlPlaneUrl = "http://localhost:8080";

    // 本服务信息
    private static String serviceName;
    private static String host;
    private static int port;
    private static String version;
    private static String instanceId;

    // 服务元数据（接口方法信息）
    private static String serviceMetadata;

    // 心跳定时任务
    private static ScheduledExecutorService heartbeatScheduler;

    // 是否已注册
    private static volatile boolean registered = false;

    /**
     * 初始化服务注册客户端
     *
     * @param serviceName 服务名称
     * @param host        主机地址
     * @param port        端口
     * @param version     版本号
     */
    public static void init(String serviceName, String host, int port, String version) {
        init(serviceName, host, port, version, null);
    }

    /**
     * 初始化服务注册客户端（带元数据）
     *
     * @param serviceName      服务名称
     * @param host             主机地址
     * @param port             端口
     * @param version          版本号
     * @param serviceMetadata  服务元数据（JSON格式的方法信息）
     */
    public static void init(String serviceName, String host, int port, String version, String serviceMetadata) {
        ServiceRegistryClient.serviceName = serviceName;
        ServiceRegistryClient.host = host;
        ServiceRegistryClient.port = port;
        ServiceRegistryClient.version = version != null ? version : "";
        ServiceRegistryClient.serviceMetadata = serviceMetadata;

        // 生成实例 ID
        instanceId = generateInstanceId();

        // 注册服务实例
        register();

        // 启动心跳任务（每 30 秒发送一次）
        startHeartbeat();
    }

    /**
     * 生成实例 ID
     *
     * 格式：{serviceName}@{host}:{port}
     * 无论重启多少次，同一台机器上的同一个服务，其 ID 永远保持一致
     */
    private static String generateInstanceId() {
        return serviceName + "@" + host + ":" + port;
    }

    /**
     * 向控制平面注册服务实例
     */
    public static void register() {
        registerWithRetry(0);
    }

    /**
     * 带重试机制的注册方法
     *
     * @param retryCount 当前重试次数
     */
    private static void registerWithRetry(int retryCount) {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/register";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("serviceName", serviceName);
            requestBody.put("instanceId", instanceId);
            requestBody.put("host", host);
            requestBody.put("port", port);
            requestBody.put("version", version);

            // 添加服务元数据
            if (serviceMetadata != null && !serviceMetadata.isEmpty()) {
                requestBody.put("serviceMetadata", serviceMetadata);
            }

            String json = objectMapper.writeValueAsString(requestBody);

            // ==================== 高亮日志：打印完整的注册 JSON ====================
            logger.info("📤 [Service Registration] Sending registration request to: {}", url);
            logger.info("📋 [Service Registration] Registration JSON payload:\n{}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                registered = true;
                logger.info("✅ Registered service instance: {} at {}:{} with metadata",
                        serviceName, host, port);
            } else {
                logger.warn("Failed to register service instance: HTTP {}, body: {}",
                        response.statusCode(), response.body());
                // 非成功状态也尝试重试
                attemptRetry(retryCount);
            }

        } catch (ConnectException e) {
            // 连接被拒绝（控制平面可能还没启动）
            logger.warn("⚠️ [Service Registration] Connection refused to control plane: {} (attempt {}/{})",
                    controlPlaneUrl, retryCount + 1, MAX_RETRY_COUNT);
            logger.warn("⚠️ [Service Registration] This is usually because Control Plane is not started yet. " +
                    "Service will continue to run, but won't be registered.");
            attemptRetry(retryCount);

        } catch (Exception e) {
            logger.warn("⚠️ [Service Registration] Failed to register service instance (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRY_COUNT, e.getMessage());
            attemptRetry(retryCount);
        }
    }

    /**
     * 尝试重试注册
     */
    private static void attemptRetry(int currentRetryCount) {
        if (currentRetryCount < MAX_RETRY_COUNT) {
            int nextRetryCount = currentRetryCount + 1;
            logger.info("🔄 [Service Registration] Retrying in {} seconds... (attempt {}/{})",
                    RETRY_DELAY_SECONDS, nextRetryCount + 1, MAX_RETRY_COUNT);

            // 使用调度器延迟重试
            if (heartbeatScheduler == null) {
                heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "service-registry-retry");
                    t.setDaemon(true);
                    return t;
                });
            }

            heartbeatScheduler.schedule(
                    () -> registerWithRetry(nextRetryCount),
                    RETRY_DELAY_SECONDS,
                    TimeUnit.SECONDS
            );
        } else {
            logger.warn("❌ [Service Registration] Max retry count ({}) reached. " +
                    "Service will continue without registration. Please check if Control Plane is running at: {}",
                    MAX_RETRY_COUNT, controlPlaneUrl);
            // 不抛出异常，允许服务继续运行
        }
    }

    /**
     * 发送心跳（高可用加固版）
     *
     * 关键防御机制：
     * 1. 严格的 try-catch (Exception e)：任何异常都不允许抛出，防止中断定时器
     * 2. 快速超时：连接 5 秒，读取 10 秒，防止线程阻塞
     * 3. 详细错误日志：记录失败原因便于排查
     */
    public static void heartbeat() {
        if (!registered) {
            // 如果还未注册成功，尝试重新注册
            logger.debug("Service not registered yet, attempting re-registration...");
            registerWithRetry(0);
            return;
        }

        try {
            String url = controlPlaneUrl + "/api/v1/registry/heartbeat/" + instanceId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.debug("Heartbeat sent successfully for instance: {}", instanceId);
            } else if (response.statusCode() == 404) {
                // 实例不存在，需要重新注册
                logger.warn("Instance not found on control plane (HTTP 404), will re-register: {}", instanceId);
                registered = false;
                registerWithRetry(0);
            } else {
                logger.warn("Heartbeat returned unexpected status: HTTP {} for instance: {}",
                        response.statusCode(), instanceId);
            }

        } catch (java.net.http.HttpTimeoutException e) {
            // 超时异常：记录但不中断定时器
            logger.error("❌ [HA-Heartbeat] Timeout sending heartbeat for {}: {}. " +
                    "Control plane may be overloaded or network congested.", instanceId, e.getMessage());
        } catch (java.net.ConnectException e) {
            // 连接异常：控制面可能暂时不可用
            logger.error("❌ [HA-Heartbeat] Connection refused for {}: {}. " +
                    "Control plane may be temporarily unavailable.", instanceId, e.getMessage());
        } catch (InterruptedException e) {
            // 中断异常：恢复中断状态但不抛出
            logger.error("❌ [HA-Heartbeat] Interrupted while sending heartbeat for {}: {}",
                    instanceId, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 绝对不允许任何异常中断定时器！
            logger.error("❌ [HA-Heartbeat] Unexpected error sending heartbeat for {}: {} - {}",
                    instanceId, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * 注销服务实例
     */
    public static void deregister() {
        try {
            String url = controlPlaneUrl + "/api/v1/registry/deregister/" + instanceId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Deregistered service instance: {}", instanceId);

        } catch (Exception e) {
            logger.warn("Failed to deregister service instance: {}", instanceId, e);
        }
    }

    /**
     * 启动心跳任务
     */
    private static void startHeartbeat() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "service-heartbeat");
                t.setDaemon(true);
                return t;
            });

            // 每 30 秒发送一次心跳
            heartbeatScheduler.scheduleAtFixedRate(
                    ServiceRegistryClient::heartbeat,
                    30,
                    30,
                    TimeUnit.SECONDS
            );

            logger.info("Service heartbeat task started");
        }
    }

    /**
     * 停止服务注册客户端
     */
    public static void shutdown() {
        logger.info("🛑 [Graceful Shutdown] Stopping service registry client...");

        // 注销服务实例
        if (registered) {
            deregister();
        }

        // 停止心跳任务
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("✅ [Graceful Shutdown] Service registry client stopped");
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

    /**
     * 检查是否已注册
     */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * 设置服务元数据
     */
    public static void setServiceMetadata(String metadata) {
        serviceMetadata = metadata;
    }

    /**
     * 获取服务元数据
     */
    public static String getServiceMetadata() {
        return serviceMetadata;
    }
}