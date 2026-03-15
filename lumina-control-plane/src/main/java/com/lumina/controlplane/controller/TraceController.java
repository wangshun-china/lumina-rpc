package com.lumina.controlplane.controller;

import com.lumina.controlplane.dto.SpanDto;
import com.lumina.controlplane.dto.TraceDetailDto;
import com.lumina.controlplane.dto.TraceSummaryDto;
import com.lumina.controlplane.dto.ServiceStatsDto;
import com.lumina.controlplane.service.TraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路追踪 Controller
 */
@RestController
@RequestMapping("/api/v1/traces")
@CrossOrigin(origins = "*")
public class TraceController {

    private static final Logger logger = LoggerFactory.getLogger(TraceController.class);

    @Autowired
    private TraceService traceService;

    /**
     * 上报 Span
     */
    @PostMapping("/spans")
    public ResponseEntity<Map<String, Object>> reportSpan(@RequestBody SpanDto span) {
        try {
            traceService.saveSpan(span);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("spanId", span.getSpanId());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to save span: {}", e.getMessage(), e);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取 Trace 列表
     */
    @GetMapping
    public ResponseEntity<?> getTraces(
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<TraceSummaryDto> traces = traceService.getRecentTraces(limit);
            return ResponseEntity.ok(traces);
        } catch (Exception e) {
            logger.error("Failed to get traces: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("traces", List.of());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * 获取 Trace 详情
     */
    @GetMapping("/{traceId}")
    public ResponseEntity<TraceDetailDto> getTraceDetail(@PathVariable String traceId) {
        try {
            TraceDetailDto detail = traceService.getTraceDetail(traceId);
            if (detail == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            logger.error("Failed to get trace detail: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务统计
     */
    @GetMapping("/stats/services")
    public ResponseEntity<List<ServiceStatsDto>> getServiceStats(
            @RequestParam(defaultValue = "1") int hours) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hours);

            List<ServiceStatsDto> stats = traceService.getServiceStats(startTime, endTime);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to get service stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}