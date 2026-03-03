package com.lumina.controlplane.controller;

import com.lumina.controlplane.service.MockRuleService;
import com.lumina.controlplane.service.ServiceInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统计面板控制器
 * 提供系统整体统计信息
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

    private final ServiceInstanceService serviceInstanceService;
    private final MockRuleService mockRuleService;

    public StatsController(ServiceInstanceService serviceInstanceService,
                          MockRuleService mockRuleService) {
        this.serviceInstanceService = serviceInstanceService;
        this.mockRuleService = mockRuleService;
    }

    /**
     * 获取统计面板数据
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        logger.debug("Getting stats for dashboard");

        Map<String, Object> stats = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();

        // 服务实例统计
        long totalServices = serviceInstanceService.countDistinctHealthyServices();
        long totalInstances = serviceInstanceService.countHealthyInstances();

        stats.put("onlineServices", totalServices);
        stats.put("totalInstances", totalInstances);

        // Mock 规则统计
        long enabledRules = mockRuleService.countEnabledRules();
        long totalRules = mockRuleService.countAll();

        stats.put("enabledMockRules", enabledRules);
        stats.put("totalMockRules", totalRules);

        // 请求统计（暂时为0，后续可以从日志或 metrics 中获取）
        stats.put("todayRequests", 0);
        stats.put("avgLatency", 0);

        // 系统状态
        stats.put("systemStatus", "UP");
        stats.put("timestamp", now.toString());

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取服务注册统计
     */
    @GetMapping("/registry")
    public ResponseEntity<Map<String, Object>> getRegistryStats() {
        logger.debug("Getting registry stats");

        Map<String, Object> stats = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();

        long totalInstances = serviceInstanceService.findAll().size();
        long healthyInstances = serviceInstanceService.findHealthyInstances().size();
        long distinctServices = serviceInstanceService.countDistinctHealthyServices();

        stats.put("totalInstances", totalInstances);
        stats.put("healthyInstances", healthyInstances);
        stats.put("distinctServices", distinctServices);
        stats.put("unhealthyInstances", totalInstances - healthyInstances);

        return ResponseEntity.ok(stats);
    }

    /**
     * 获取 Mock 规则统计
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getRuleStats() {
        logger.debug("Getting rule stats");

        Map<String, Object> stats = new HashMap<>();

        long totalRules = mockRuleService.countAll();
        long enabledRules = mockRuleService.countEnabledRules();

        stats.put("totalRules", totalRules);
        stats.put("enabledRules", enabledRules);
        stats.put("disabledRules", totalRules - enabledRules);

        return ResponseEntity.ok(stats);
    }
}
