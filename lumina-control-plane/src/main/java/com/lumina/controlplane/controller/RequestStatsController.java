package com.lumina.controlplane.controller;

import com.lumina.controlplane.service.RequestStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求统计控制器
 *
 * 提供请求趋势和服务统计数据
 */
@RestController
@RequestMapping("/api/v1/stats")
public class RequestStatsController {

    private final RequestStatsService statsService;

    public RequestStatsController(RequestStatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 获取趋势数据（默认最近30分钟）
     */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "30") int minutes) {
        Map<String, Object> result = new HashMap<>();
        result.put("trend", statsService.getTrendData(minutes));
        result.put("minutes", minutes);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务统计
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getServiceStats(
            @RequestParam(defaultValue = "60") int minutes) {
        Map<String, Object> result = new HashMap<>();
        result.put("services", statsService.getServiceStats(minutes));
        result.put("minutes", minutes);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取实时统计
     */
    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeStats() {
        return ResponseEntity.ok(statsService.getRealtimeStats());
    }

    /**
     * 上报请求数据（Consumer 端调用）
     */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> reportRequest(
            @RequestBody Map<String, Object> data) {

        String serviceName = (String) data.get("serviceName");
        Boolean success = (Boolean) data.getOrDefault("success", true);
        Number latency = (Number) data.get("latency");

        if (serviceName != null && latency != null) {
            statsService.recordRequest(serviceName, success, latency.longValue());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量上报请求数据
     */
    @PostMapping("/report/batch")
    public ResponseEntity<Map<String, Object>> reportBatch(
            @RequestBody List<Map<String, Object>> requests) {

        for (Map<String, Object> data : requests) {
            String serviceName = (String) data.get("serviceName");
            Boolean success = (Boolean) data.getOrDefault("success", true);
            Number latency = (Number) data.get("latency");

            if (serviceName != null && latency != null) {
                statsService.recordRequest(serviceName, success, latency.longValue());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", requests.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 聚合上报请求数据（Consumer 端定时上报）
     */
    @PostMapping("/report/aggregate")
    public ResponseEntity<Map<String, Object>> reportAggregate(
            @RequestBody List<Map<String, Object>> aggregates) {

        for (Map<String, Object> data : aggregates) {
            String serviceName = (String) data.get("serviceName");
            Number successCount = (Number) data.get("successCount");
            Number failCount = (Number) data.get("failCount");
            Number avgLatency = (Number) data.get("avgLatency");

            if (serviceName != null && successCount != null) {
                statsService.recordAggregate(
                        serviceName,
                        successCount.longValue(),
                        failCount != null ? failCount.longValue() : 0,
                        avgLatency != null ? avgLatency.longValue() : 0
                );
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", aggregates.size());
        return ResponseEntity.ok(result);
    }
}