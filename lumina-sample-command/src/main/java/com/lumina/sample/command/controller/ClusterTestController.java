package com.lumina.sample.command.controller;

import com.lumina.sample.command.service.ClusterTestClient;
import com.lumina.sample.engine.service.EngineService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群容错策略测试控制器
 *
 * 用于验证 Failover、Failfast、Failsafe、Forking 四种策略的行为
 */
@Slf4j
@RestController
@RequestMapping("/api/cluster-test")
public class ClusterTestController {

    private final ClusterTestClient clusterTestClient;

    public ClusterTestController(ClusterTestClient clusterTestClient) {
        this.clusterTestClient = clusterTestClient;
    }

    /**
     * 测试单个集群策略
     *
     * @param strategy 策略名称: failover, failfast, failsafe, forking
     * @param shouldFail 是否模拟失败
     */
    @GetMapping("/invoke")
    public ResponseEntity<Map<String, Object>> testInvoke(
            @RequestParam(defaultValue = "failover") String strategy,
            @RequestParam(defaultValue = "false") boolean shouldFail) {

        log.info("🧪 [Cluster-Test] Testing strategy: {}, shouldFail: {}", strategy, shouldFail);

        Map<String, Object> result = new HashMap<>();
        result.put("strategy", strategy);
        result.put("shouldFail", shouldFail);
        result.put("timestamp", System.currentTimeMillis());

        EngineService client = getClient(strategy);
        if (client == null) {
            result.put("success", false);
            result.put("error", "Unknown strategy: " + strategy);
            return ResponseEntity.ok(result);
        }

        long startTime = System.currentTimeMillis();
        try {
            String response = client.testCluster(shouldFail);
            long duration = System.currentTimeMillis() - startTime;

            result.put("success", true);
            result.put("data", response);
            result.put("durationMs", duration);
            log.info("✅ [Cluster-Test] {} succeeded in {}ms: {}", strategy, duration, response);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("durationMs", duration);
            log.error("❌ [Cluster-Test] {} failed in {}ms: {}", strategy, duration, e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 批量测试所有策略
     *
     * @param shouldFail 是否模拟失败
     */
    @GetMapping("/invoke-all")
    public ResponseEntity<Map<String, Object>> testAllStrategies(
            @RequestParam(defaultValue = "false") boolean shouldFail) {

        log.info("🧪 [Cluster-Test] Testing ALL strategies, shouldFail: {}", shouldFail);

        String[] strategies = {"failover", "failfast", "failsafe", "forking"};
        List<Map<String, Object>> results = new ArrayList<>();

        for (String strategy : strategies) {
            Map<String, Object> testResult = new HashMap<>();
            testResult.put("strategy", strategy);

            EngineService client = getClient(strategy);
            if (client == null) {
                testResult.put("success", false);
                testResult.put("error", "Client not initialized");
                results.add(testResult);
                continue;
            }

            long startTime = System.currentTimeMillis();
            try {
                String response = client.testCluster(shouldFail);
                long duration = System.currentTimeMillis() - startTime;

                testResult.put("success", true);
                testResult.put("data", response);
                testResult.put("durationMs", duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                testResult.put("success", false);
                testResult.put("error", e.getMessage());
                testResult.put("errorType", e.getClass().getSimpleName());
                testResult.put("durationMs", duration);
            }

            results.add(testResult);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("shouldFail", shouldFail);
        response.put("results", results);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 测试说明
     */
    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("title", "Lumina-RPC 集群容错策略测试");

        List<Map<String, String>> strategies = new ArrayList<>();

        Map<String, String> failover = new HashMap<>();
        failover.put("name", "failover");
        failover.put("description", "失败自动重试其他服务器");
        failover.put("behavior", "shouldFail=true 时会重试，多实例下可能成功");
        failover.put("useCase", "读操作、幂等查询");
        strategies.add(failover);

        Map<String, String> failfast = new HashMap<>();
        failfast.put("name", "failfast");
        failfast.put("description", "快速失败，只发起一次调用");
        failfast.put("behavior", "shouldFail=true 时立即抛出异常");
        failfast.put("useCase", "非幂等写操作、下单、扣款");
        strategies.add(failfast);

        Map<String, String> failsafe = new HashMap<>();
        failsafe.put("name", "failsafe");
        failsafe.put("description", "失败安全，异常直接忽略");
        failsafe.put("behavior", "shouldFail=true 时返回 null，不抛异常");
        failsafe.put("useCase", "审计日志、非核心业务");
        strategies.add(failsafe);

        Map<String, String> forking = new HashMap<>();
        forking.put("name", "forking");
        forking.put("description", "并行调用多个服务器，一个成功即返回");
        forking.put("behavior", "多实例下同时调用，取最快成功结果");
        forking.put("useCase", "实时性要求高的读操作");
        strategies.add(forking);

        help.put("strategies", strategies);

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /api/cluster-test/invoke?strategy=failover&shouldFail=false",
                "测试单个策略");
        endpoints.put("GET /api/cluster-test/invoke-all?shouldFail=false",
                "批量测试所有策略");
        endpoints.put("GET /api/cluster-test/help",
                "获取测试说明");
        help.put("endpoints", endpoints);

        Map<String, String> testScenarios = new HashMap<>();
        testScenarios.put("场景1: 正常调用", "shouldFail=false，所有策略应返回成功");
        testScenarios.put("场景2: 模拟失败", "shouldFail=true，观察不同策略的行为差异");
        testScenarios.put("场景3: 多实例测试", "启动多个 Engine 实例，测试 Failover/Forking");
        help.put("testScenarios", testScenarios);

        return ResponseEntity.ok(help);
    }

    /**
     * 根据策略名获取客户端
     */
    private EngineService getClient(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "failover" -> clusterTestClient.getFailoverClient();
            case "failfast" -> clusterTestClient.getFailfastClient();
            case "failsafe" -> clusterTestClient.getFailsafeClient();
            case "forking" -> clusterTestClient.getForkingClient();
            default -> null;
        };
    }
}