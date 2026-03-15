package com.lumina.controlplane.controller;

import com.lumina.controlplane.entity.ShutdownConfigEntity;
import com.lumina.controlplane.service.ShutdownConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 优雅停机配置控制器
 *
 * 提供：
 * 1. 查看所有服务的停机状态
 * 2. 配置停机超时时间
 * 3. 触发/取消停机
 * 4. Provider 上报活跃请求数
 */
@RestController
@RequestMapping("/api/v1/shutdown")
public class ShutdownConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownConfigController.class);

    private final ShutdownConfigService service;

    public ShutdownConfigController(ShutdownConfigService service) {
        this.service = service;
    }

    /**
     * 获取所有服务的停机状态
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        List<ShutdownConfigEntity> configs = service.findAll();

        Map<String, Object> result = new HashMap<>();
        result.put("configs", configs);
        result.put("total", configs.size());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取停机状态概览
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        ShutdownConfigService.ShutdownStatusOverview overview = service.getStatusOverview();

        Map<String, Object> result = new HashMap<>();
        result.put("totalServices", overview.totalServices);
        result.put("running", overview.running);
        result.put("shuttingDown", overview.shuttingDown);
        result.put("totalActiveRequests", overview.totalActiveRequests);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定服务的停机配置
     */
    @GetMapping("/configs/{serviceName}")
    public ResponseEntity<ShutdownConfigEntity> getConfig(@PathVariable String serviceName) {
        ShutdownConfigEntity config = service.findByServiceName(serviceName);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 更新停机配置（超时时间等）
     */
    @PutMapping("/configs/{serviceName}")
    public ResponseEntity<ShutdownConfigEntity> updateConfig(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> params) {

        logger.info("Updating shutdown config for service: {}", serviceName);

        Boolean enabled = params.get("enabled") != null ? (Boolean) params.get("enabled") : null;
        Long timeoutMs = params.get("timeoutMs") != null ? ((Number) params.get("timeoutMs")).longValue() : null;

        ShutdownConfigEntity config = service.updateConfig(serviceName, enabled, timeoutMs);
        return ResponseEntity.ok(config);
    }

    /**
     * 触发优雅停机
     */
    @PostMapping("/configs/{serviceName}/trigger")
    public ResponseEntity<Map<String, Object>> triggerShutdown(@PathVariable String serviceName) {
        logger.info("🛑 Triggering graceful shutdown for service: {}", serviceName);

        ShutdownConfigEntity config = service.triggerShutdown(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Shutdown triggered for: " + serviceName);
        result.put("config", config);

        return ResponseEntity.ok(result);
    }

    /**
     * 取消停机
     */
    @PostMapping("/configs/{serviceName}/cancel")
    public ResponseEntity<Map<String, Object>> cancelShutdown(@PathVariable String serviceName) {
        logger.info("✅ Cancelling shutdown for service: {}", serviceName);

        ShutdownConfigEntity config = service.cancelShutdown(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Shutdown cancelled for: " + serviceName);
        result.put("config", config);

        return ResponseEntity.ok(result);
    }

    /**
     * Provider 上报活跃请求数（用于前端显示）
     */
    @PostMapping("/configs/{serviceName}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> data) {

        Integer activeRequests = data.get("activeRequests") != null
                ? ((Number) data.get("activeRequests")).intValue()
                : 0;

        service.updateActiveRequests(serviceName, activeRequests);

        // 返回最新的配置（让 Provider 同步）
        ShutdownConfigEntity config = service.findByServiceName(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        // 如果配置了停机，返回停机信号
        if (config != null) {
            result.put("shuttingDown", config.getShuttingDown());
            result.put("timeoutMs", config.getTimeoutMs());
            result.put("enabled", config.getEnabled());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/configs/{serviceName}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String serviceName) {
        logger.info("Deleting shutdown config for service: {}", serviceName);
        service.delete(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Config deleted for: " + serviceName);
        return ResponseEntity.ok(result);
    }
}