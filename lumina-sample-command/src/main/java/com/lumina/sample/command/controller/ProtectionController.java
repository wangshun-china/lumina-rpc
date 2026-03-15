package com.lumina.sample.command.controller;

import com.lumina.rpc.core.circuitbreaker.CircuitBreaker;
import com.lumina.rpc.core.circuitbreaker.CircuitBreakerManager;
import com.lumina.rpc.core.circuitbreaker.RateLimiter;
import com.lumina.rpc.core.circuitbreaker.RateLimiterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 熔断器和限流器状态控制器
 *
 * 提供熔断器和限流器的状态查询和操作接口
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/protection")
public class ProtectionController {

    // ==================== 熔断器 API ====================

    /**
     * 获取所有熔断器状态
     */
    @GetMapping("/circuit-breaker")
    public ResponseEntity<Map<String, Object>> getAllCircuitBreakers() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> breakers = new ArrayList<>();

        CircuitBreakerManager manager = CircuitBreakerManager.getInstance();
        for (Map.Entry<String, CircuitBreaker> entry : manager.getAllCircuitBreakers().entrySet()) {
            CircuitBreaker cb = entry.getValue();

            Map<String, Object> breakerInfo = new HashMap<>();
            breakerInfo.put("serviceName", entry.getKey());
            breakerInfo.put("state", cb.getState().name());
            breakerInfo.put("totalRequests", cb.getStats());
            breakers.add(breakerInfo);
        }

        result.put("circuitBreakers", breakers);
        result.put("total", breakers.size());
        result.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定服务的熔断器状态
     */
    @GetMapping("/circuit-breaker/{serviceName}")
    public ResponseEntity<Map<String, Object>> getCircuitBreaker(@PathVariable String serviceName) {
        CircuitBreakerManager manager = CircuitBreakerManager.getInstance();
        CircuitBreaker cb = manager.getAllCircuitBreakers().get(serviceName);

        if (cb == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("serviceName", serviceName);
        result.put("state", cb.getState().name());
        result.put("stats", cb.getStats());

        return ResponseEntity.ok(result);
    }

    /**
     * 重置熔断器
     */
    @PostMapping("/circuit-breaker/{serviceName}/reset")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker(@PathVariable String serviceName) {
        log.info("Resetting circuit breaker for service: {}", serviceName);

        CircuitBreakerManager manager = CircuitBreakerManager.getInstance();
        manager.reset(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("serviceName", serviceName);
        result.put("message", "Circuit breaker reset successfully");

        return ResponseEntity.ok(result);
    }

    /**
     * 重置所有熔断器
     */
    @PostMapping("/circuit-breaker/reset-all")
    public ResponseEntity<Map<String, Object>> resetAllCircuitBreakers() {
        log.info("Resetting all circuit breakers");

        CircuitBreakerManager manager = CircuitBreakerManager.getInstance();
        manager.resetAll();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "All circuit breakers reset successfully");

        return ResponseEntity.ok(result);
    }

    // ==================== 限流器 API ====================

    /**
     * 获取所有限流器状态
     */
    @GetMapping("/rate-limiter")
    public ResponseEntity<Map<String, Object>> getAllRateLimiters() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> limiters = new ArrayList<>();

        RateLimiterManager manager = RateLimiterManager.getInstance();
        for (Map.Entry<String, RateLimiter> entry : manager.getAllRateLimiters().entrySet()) {
            RateLimiter limiter = entry.getValue();

            Map<String, Object> limiterInfo = new HashMap<>();
            limiterInfo.put("serviceName", entry.getKey());
            limiterInfo.put("permitsPerSecond", limiter.getPermitsPerSecond());
            limiterInfo.put("availableTokens", limiter.getAvailableTokens());
            limiterInfo.put("passedCount", limiter.getPassedCount());
            limiterInfo.put("rejectedCount", limiter.getRejectedCount());
            limiters.add(limiterInfo);
        }

        result.put("rateLimiters", limiters);
        result.put("total", limiters.size());
        result.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定服务的限流器状态
     */
    @GetMapping("/rate-limiter/{serviceName}")
    public ResponseEntity<Map<String, Object>> getRateLimiter(@PathVariable String serviceName) {
        RateLimiterManager manager = RateLimiterManager.getInstance();
        RateLimiter limiter = manager.getAllRateLimiters().get(serviceName);

        if (limiter == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("serviceName", serviceName);
        result.put("permitsPerSecond", limiter.getPermitsPerSecond());
        result.put("availableTokens", limiter.getAvailableTokens());
        result.put("passedCount", limiter.getPassedCount());
        result.put("rejectedCount", limiter.getRejectedCount());
        result.put("stats", limiter.getStats());

        return ResponseEntity.ok(result);
    }

    /**
     * 更新限流阈值
     */
    @PutMapping("/rate-limiter/{serviceName}")
    public ResponseEntity<Map<String, Object>> updateRateLimiter(
            @PathVariable String serviceName,
            @RequestParam int permits) {

        log.info("Updating rate limiter for service: {} to {} permits/s", serviceName, permits);

        RateLimiterManager manager = RateLimiterManager.getInstance();
        manager.updatePermits(serviceName, permits);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("serviceName", serviceName);
        result.put("permitsPerSecond", permits);
        result.put("message", "Rate limiter updated successfully");

        return ResponseEntity.ok(result);
    }

    /**
     * 重置限流器统计
     */
    @PostMapping("/rate-limiter/{serviceName}/reset")
    public ResponseEntity<Map<String, Object>> resetRateLimiter(@PathVariable String serviceName) {
        log.info("Resetting rate limiter for service: {}", serviceName);

        RateLimiterManager manager = RateLimiterManager.getInstance();
        manager.reset(serviceName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("serviceName", serviceName);
        result.put("message", "Rate limiter reset successfully");

        return ResponseEntity.ok(result);
    }

    // ==================== 综合状态 API ====================

    /**
     * 获取所有保护机制状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getProtectionStatus() {
        Map<String, Object> result = new HashMap<>();

        // 熔断器统计
        CircuitBreakerManager cbManager = CircuitBreakerManager.getInstance();
        int openCount = 0;
        int closedCount = 0;
        int halfOpenCount = 0;

        for (CircuitBreaker cb : cbManager.getAllCircuitBreakers().values()) {
            switch (cb.getState()) {
                case OPEN -> openCount++;
                case CLOSED -> closedCount++;
                case HALF_OPEN -> halfOpenCount++;
            }
        }

        Map<String, Object> cbStats = new HashMap<>();
        cbStats.put("total", cbManager.getAllCircuitBreakers().size());
        cbStats.put("open", openCount);
        cbStats.put("closed", closedCount);
        cbStats.put("halfOpen", halfOpenCount);
        result.put("circuitBreaker", cbStats);

        // 限流器统计
        RateLimiterManager rlManager = RateLimiterManager.getInstance();
        long totalPassed = 0;
        long totalRejected = 0;

        for (RateLimiter limiter : rlManager.getAllRateLimiters().values()) {
            totalPassed += limiter.getPassedCount();
            totalRejected += limiter.getRejectedCount();
        }

        Map<String, Object> rlStats = new HashMap<>();
        rlStats.put("total", rlManager.getAllRateLimiters().size());
        rlStats.put("totalPassed", totalPassed);
        rlStats.put("totalRejected", totalRejected);
        result.put("rateLimiter", rlStats);

        result.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(result);
    }
}