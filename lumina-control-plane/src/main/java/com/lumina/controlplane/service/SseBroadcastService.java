package com.lumina.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 广播服务 - 核心组件
 * 管理所有 SseEmitter 连接，当 Mock Rule 变化时广播给所有 Consumer
 */
@Service
public class SseBroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(SseBroadcastService.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30分钟超时
    private static final long HEARTBEAT_INTERVAL = 30; // 30秒心跳

    private final ObjectMapper objectMapper;

    // 存储所有活跃的 SseEmitter，按 serviceName 分组
    private final Map<String, Set<SseEmitter>> emittersByService = new ConcurrentHashMap<>();

    // 存储所有 SseEmitter 到 serviceName 的映射（用于快速查找）
    private final Map<SseEmitter, String> emitterToService = new ConcurrentHashMap<>();

    // 心跳任务执行器
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseBroadcastService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        startHeartbeat();
    }

    /**
     * 创建并注册一个新的 SseEmitter
     *
     * @param serviceName 服务名称
     * @return SseEmitter 实例
     */
    public SseEmitter createEmitter(String serviceName) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 存储映射关系
        emittersByService.computeIfAbsent(serviceName, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitterToService.put(emitter, serviceName);

        logger.info("Created new SSE emitter for service: {}, total emitters for service: {}",
                serviceName, emittersByService.get(serviceName).size());

        // 注册完成回调
        emitter.onCompletion(() -> {
            logger.debug("SSE emitter completed for service: {}", serviceName);
            removeEmitter(emitter);
        });

        // 注册超时回调
        emitter.onTimeout(() -> {
            logger.warn("SSE emitter timed out for service: {}", serviceName);
            removeEmitter(emitter);
        });

        // 注册错误回调
        emitter.onError(e -> {
            logger.error("SSE emitter error for service: {}", serviceName, e);
            removeEmitter(emitter);
        });

        // 发送连接成功事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"service\":\"" + serviceName + "\"}"));
        } catch (IOException e) {
            logger.error("Failed to send connection event", e);
            removeEmitter(emitter);
        }

        return emitter;
    }

    /**
     * 移除一个 SseEmitter
     */
    private void removeEmitter(SseEmitter emitter) {
        String serviceName = emitterToService.remove(emitter);
        if (serviceName != null) {
            Set<SseEmitter> emitters = emittersByService.get(serviceName);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    emittersByService.remove(serviceName);
                }
                logger.info("Removed SSE emitter for service: {}, remaining: {}",
                        serviceName, emitters.size());
            }
        }
    }

    /**
     * 广播 Mock Rule 变更事件 - 统一使用 config-change 格式
     *
     * @param serviceName 服务名称
     * @param ruleId      变更的规则 ID
     * @param action      操作类型: CREATE, UPDATE, DELETE
     */
    public void broadcastRuleChange(String serviceName, Long ruleId, String action) {
        // 使用统一的 config-change 格式广播
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("ruleId", ruleId);
        data.put("action", action);

        broadcastConfigChange("mock", serviceName, data);
    }

    /**
     * 广播给指定 emitter 组的订阅者（保留兼容）
     */
    private void broadcastToService(String emitterKey, String serviceName, Long ruleId, String action) {
        Set<SseEmitter> emitters = emittersByService.get(emitterKey);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(new RuleChangeEvent(ruleId, action, serviceName));
        } catch (Exception e) {
            logger.error("Failed to serialize rule change event", e);
            return;
        }

        logger.info("📡 Broadcasting rule change to {} emitters (key={}), service={}, action={}",
                emitters.size(), emitterKey, serviceName, action);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("rule-change")
                        .id(String.valueOf(ruleId))
                        .data(eventData));
            } catch (IOException e) {
                logger.error("Failed to send SSE event to emitter", e);
                removeEmitter(emitter);
            }
        }
    }

    /**
     * 广播到所有服务
     */
    public void broadcastToAll(Long ruleId, String action) {
        for (String serviceName : emittersByService.keySet()) {
            broadcastRuleChange(serviceName, ruleId, action);
        }
    }

    /**
     * 启动心跳任务
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                logger.error("Error during heartbeat", e);
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

        logger.info("SSE heartbeat started with interval: {} seconds", HEARTBEAT_INTERVAL);
    }

    /**
     * 发送心跳保持连接
     */
    private void sendHeartbeat() {
        if (emittersByService.isEmpty()) {
            return;
        }

        String heartbeatData = "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";

        for (Map.Entry<String, Set<SseEmitter>> entry : emittersByService.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(heartbeatData));
                } catch (IOException e) {
                    logger.debug("Failed to send heartbeat, removing emitter", e);
                    removeEmitter(emitter);
                }
            }
        }
    }

    /**
     * 获取当前活跃连接统计
     */
    public Map<String, Integer> getConnectionStats() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, Set<SseEmitter>> entry : emittersByService.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    /**
     * 关闭所有连接（应用关闭时）
     */
    public void shutdown() {
        logger.info("Shutting down SSE broadcast service...");
        heartbeatExecutor.shutdown();

        for (SseEmitter emitter : emitterToService.keySet()) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.debug("Error completing emitter during shutdown", e);
            }
        }

        emittersByService.clear();
        emitterToService.clear();
        logger.info("SSE broadcast service shutdown complete");
    }

    /**
     * 规则变更事件内部类
     */
    public static class RuleChangeEvent {
        private Long ruleId;
        private String action;
        private String serviceName;
        private Long timestamp;

        public RuleChangeEvent(Long ruleId, String action, String serviceName) {
            this.ruleId = ruleId;
            this.action = action;
            this.serviceName = serviceName;
            this.timestamp = System.currentTimeMillis();
        }

        public Long getRuleId() {
            return ruleId;
        }

        public void setRuleId(Long ruleId) {
            this.ruleId = ruleId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }

    // ==================== 统一配置变更广播 ====================

    /**
     * 广播统一配置变更事件
     * 用于 ProtectionConfig、MockRule 等所有配置变更
     *
     * @param configType 配置类型: protection | mock | shutdown
     * @param serviceName 服务名称
     * @param configData 配置数据对象
     */
    public void broadcastConfigChange(String configType, String serviceName, Object configData) {
        String eventData;
        try {
            ConfigChangeEvent event = new ConfigChangeEvent(configType, serviceName, configData);
            eventData = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            logger.error("❌ Failed to serialize config change event", e);
            return;
        }

        int successCount = 0;
        int failCount = 0;

        // 广播给所有订阅者
        for (Map.Entry<String, Set<SseEmitter>> entry : emittersByService.entrySet()) {
            String emitterKey = entry.getKey();
            Set<SseEmitter> emitters = entry.getValue();

            if (emitters == null || emitters.isEmpty()) {
                continue;
            }

            // 只广播给订阅"all"或特定服务的消费者
            if (!"__ALL__".equals(emitterKey) && !serviceName.equals(emitterKey)) {
                continue;
            }

            logger.info("📡 [SSE-CONFIG] Broadcasting {} config change to {} ({} connections), service={}",
                    configType, emitterKey, emitters.size(), serviceName);

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("config-change")
                            .data(eventData));
                    successCount++;
                } catch (IOException e) {
                    logger.error("❌ [SSE-CONFIG] Failed to broadcast config change", e);
                    removeEmitter(emitter);
                    failCount++;
                }
            }
        }

        logger.info("📡 [SSE-CONFIG] Config broadcast complete: success={}, fail={}", successCount, failCount);
    }

    /**
     * 统一配置变更事件
     */
    public static class ConfigChangeEvent {
        private String type;        // protection | mock | shutdown
        private String serviceName;
        private Object data;        // 配置数据
        private Long timestamp;

        public ConfigChangeEvent(String type, String serviceName, Object data) {
            this.type = type;
            this.serviceName = serviceName;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

    // ==================== 优雅停机信号推送 ====================

    /**
     * 广播停机信号给指定服务的 Provider
     *
     * @param serviceName 服务名称
     * @param timeoutMs   停机超时时间
     */
    public void broadcastShutdownSignal(String serviceName, Long timeoutMs) {
        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(new ShutdownSignalEvent(serviceName, timeoutMs));
        } catch (Exception e) {
            logger.error("❌ Failed to serialize shutdown signal event", e);
            return;
        }

        // 查找该服务的所有 SSE 连接
        Set<SseEmitter> emitters = emittersByService.get(serviceName);
        if (emitters == null || emitters.isEmpty()) {
            logger.warn("⚠️ [SSE-Shutdown] No SSE connections found for service: {}", serviceName);
            return;
        }

        logger.info("🛑 [SSE-Shutdown] Broadcasting shutdown signal to {} emitters for service: {}",
                emitters.size(), serviceName);

        int successCount = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("shutdown")
                        .data(eventData));
                successCount++;
            } catch (IOException e) {
                logger.error("❌ [SSE-Shutdown] Failed to send shutdown signal", e);
                removeEmitter(emitter);
            }
        }

        logger.info("🛑 [SSE-Shutdown] Broadcast complete: {}/{} success", successCount, emitters.size());
    }

    /**
     * 停机信号事件
     */
    public static class ShutdownSignalEvent {
        private String serviceName;
        private Long timeoutMs;
        private String action = "shutdown";
        private Long timestamp;

        public ShutdownSignalEvent(String serviceName, Long timeoutMs) {
            this.serviceName = serviceName;
            this.timeoutMs = timeoutMs;
            this.timestamp = System.currentTimeMillis();
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public Long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
