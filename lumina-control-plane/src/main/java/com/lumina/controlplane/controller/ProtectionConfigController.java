package com.lumina.controlplane.controller;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.lumina.controlplane.service.ProtectionConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 保护配置控制器（简化版，对标 Dubbo）
 *
 * 提供熔断器和限流器的动态配置 API
 * 移除版本号机制，SSE 推送已足够可靠
 */
@RestController
@RequestMapping("/api/v1/protection")
public class ProtectionConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigController.class);

    private final ProtectionConfigService service;

    public ProtectionConfigController(ProtectionConfigService service) {
        this.service = service;
    }

    /**
     * 获取所有保护配置
     */
    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        List<ProtectionConfigEntity> configs = service.findAll();

        Map<String, Object> result = new HashMap<>();
        result.put("configs", configs);
        result.put("total", configs.size());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定服务的保护配置
     */
    @GetMapping("/configs/{serviceName}")
    public ResponseEntity<ProtectionConfigEntity> getConfig(@PathVariable String serviceName) {
        ProtectionConfigEntity config = service.findByServiceName(serviceName);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    /**
     * 创建或更新保护配置
     */
    @PostMapping("/configs")
    public ResponseEntity<ProtectionConfigEntity> saveConfig(@RequestBody ProtectionConfigEntity config) {
        logger.info("Saving protection config for service: {}", config.getServiceName());
        ProtectionConfigEntity saved = service.save(config);
        return ResponseEntity.ok(saved);
    }

    /**
     * 更新熔断器配置
     */
    @PutMapping("/configs/{serviceName}/circuit-breaker")
    public ResponseEntity<ProtectionConfigEntity> updateCircuitBreaker(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> params) {

        logger.info("Updating circuit breaker config for service: {}", serviceName);

        Boolean enabled = params.get("enabled") != null ? (Boolean) params.get("enabled") : null;
        Integer threshold = params.get("threshold") != null ? ((Number) params.get("threshold")).intValue() : null;
        Long timeout = params.get("timeout") != null ? ((Number) params.get("timeout")).longValue() : null;

        ProtectionConfigEntity config = service.updateCircuitBreakerConfig(serviceName, enabled, threshold, timeout);
        return ResponseEntity.ok(config);
    }

    /**
     * 更新限流器配置
     */
    @PutMapping("/configs/{serviceName}/rate-limiter")
    public ResponseEntity<ProtectionConfigEntity> updateRateLimiter(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> params) {

        logger.info("Updating rate limiter config for service: {}", serviceName);

        Boolean enabled = params.get("enabled") != null ? (Boolean) params.get("enabled") : null;
        Integer permits = params.get("permits") != null ? ((Number) params.get("permits")).intValue() : null;

        ProtectionConfigEntity config = service.updateRateLimiterConfig(serviceName, enabled, permits);
        return ResponseEntity.ok(config);
    }

    /**
     * 更新集群配置（timeout、retries、clusterStrategy）
     */
    @PutMapping("/configs/{serviceName}/cluster")
    public ResponseEntity<ProtectionConfigEntity> updateClusterConfig(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> params) {

        logger.info("Updating cluster config for service: {}", serviceName);

        Long timeoutMs = params.get("timeoutMs") != null ? ((Number) params.get("timeoutMs")).longValue() : null;
        Integer retries = params.get("retries") != null ? ((Number) params.get("retries")).intValue() : null;
        String clusterStrategy = (String) params.get("clusterStrategy");

        ProtectionConfigEntity config = service.updateClusterConfig(serviceName, timeoutMs, retries, clusterStrategy);
        return ResponseEntity.ok(config);
    }

    /**
     * 删除保护配置
     */
    @DeleteMapping("/configs/{serviceName}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String serviceName) {
        logger.info("Deleting protection config for service: {}", serviceName);
        service.delete(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Config deleted for: " + serviceName);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量获取配置（用于 Consumer 启动时加载）
     */
    @PostMapping("/configs/batch")
    public ResponseEntity<Map<String, Object>> batchGetConfigs(@RequestBody List<String> serviceNames) {
        Map<String, Object> configs = new HashMap<>();

        for (String serviceName : serviceNames) {
            ProtectionConfigEntity config = service.findByServiceName(serviceName);
            if (config != null) {
                configs.put(serviceName, config);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("configs", configs);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取保护状态概览（供前端 Dashboard 使用）
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<ProtectionConfigEntity> configs = service.findAll();

        int circuitBreakerEnabled = 0;
        int circuitBreakerDisabled = 0;
        int rateLimiterEnabled = 0;
        int rateLimiterDisabled = 0;

        for (ProtectionConfigEntity config : configs) {
            if (config.getCircuitBreakerEnabled() != null && config.getCircuitBreakerEnabled()) {
                circuitBreakerEnabled++;
            } else {
                circuitBreakerDisabled++;
            }
            if (config.getRateLimiterEnabled() != null && config.getRateLimiterEnabled()) {
                rateLimiterEnabled++;
            } else {
                rateLimiterDisabled++;
            }
        }

        Map<String, Object> circuitBreaker = new HashMap<>();
        circuitBreaker.put("enabled", circuitBreakerEnabled);
        circuitBreaker.put("disabled", circuitBreakerDisabled);

        Map<String, Object> rateLimiter = new HashMap<>();
        rateLimiter.put("enabled", rateLimiterEnabled);
        rateLimiter.put("disabled", rateLimiterDisabled);

        Map<String, Object> result = new HashMap<>();
        result.put("circuitBreaker", circuitBreaker);
        result.put("rateLimiter", rateLimiter);
        result.put("totalServices", configs.size());

        return ResponseEntity.ok(result);
    }
}