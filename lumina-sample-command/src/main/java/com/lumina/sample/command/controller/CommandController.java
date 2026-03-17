package com.lumina.sample.command.controller;

import com.lumina.sample.command.service.EngineServiceClient;
import com.lumina.sample.command.service.RadarServiceClient;
import com.lumina.sample.command.service.TelemetryService;
import com.lumina.sample.engine.api.EngineService;
import com.lumina.sample.radar.api.RadarService;
import com.lumina.rpc.protocol.trace.TraceContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 舰队指挥控制器
 * 提供 HTTP REST 接口用于下达跃迁指令和查询遥测数据
 */
@Slf4j
@RestController
@RequestMapping("/api/command")
public class CommandController {

    private final EngineServiceClient engineServiceClient;
    private final RadarServiceClient radarServiceClient;
    private final TelemetryService telemetryService;

    /**
     * Spring 参数名发现器 - 用于提取方法参数的真实名称（如 sector, shipId）
     */
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CommandController(EngineServiceClient engineServiceClient,
                           RadarServiceClient radarServiceClient,
                           TelemetryService telemetryService) {
        this.engineServiceClient = engineServiceClient;
        this.radarServiceClient = radarServiceClient;
        this.telemetryService = telemetryService;
    }

    /**
     * 执行跃迁指令
     * 并发请求 EngineService 和 RadarService，综合判断后决定是否允许跃迁
     *
     * @param shipId 星舰ID (如 "NCC-1701")
     * @param sector 目标星区 (如 "Alpha-7")
     * @return 跃迁结果
     */
    @GetMapping("/jump/{shipId}/{sector}")
    public ResponseEntity<?> executeJump(@PathVariable String shipId, @PathVariable String sector) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 [Command] 收到跃迁指令 - Ship: {}, Target Sector: {}", shipId, sector);

        // 并发执行引擎状态查询和雷达扫描
        CompletableFuture<EngineService.WarpStatusDTO> engineFuture = CompletableFuture.supplyAsync(() ->
            engineServiceClient.getEngineStatus(shipId)
        );

        CompletableFuture<RadarService.ScanResult> radarFuture = CompletableFuture.supplyAsync(() ->
            radarServiceClient.scanEnemies(sector)
        );

        // 等待两个任务完成（带超时）
        EngineService.WarpStatusDTO engineStatus;
        RadarService.ScanResult radarResult;

        try {
            engineStatus = engineFuture.get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("💥 [Command] 跃迁中止：引擎状态查询失败 - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new JumpResponse(false, "跃迁中止：引擎状态查询失败 - " + e.getMessage(),
                            shipId, sector, null, null, System.currentTimeMillis() - startTime));
        }

        try {
            radarResult = radarFuture.get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("🌩️ [Command] 跃迁中止：雷达数据丢失 - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new JumpResponse(false, "跃迁中止：雷达数据丢失 - " + e.getMessage(),
                            shipId, sector, engineStatus, null, System.currentTimeMillis() - startTime));
        }

        // 综合判断是否允许跃迁
        long elapsedTime = System.currentTimeMillis() - startTime;
        JumpResponse response = evaluateJump(shipId, sector, engineStatus, radarResult, elapsedTime);

        if (response.isSuccess()) {
            log.info("✅ [Command] 跃迁指令已批准 - Ship: {}, Sector: {}, 敌舰: {}, 威胁: {}",
                    shipId, sector, radarResult.getEnemyCount(), radarResult.getThreatLevel());
        } else {
            log.warn("❌ [Command] 跃迁指令被拒绝 - Ship: {}, Sector: {}, 原因: {}",
                    shipId, sector, response.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 评估跃迁可行性
     */
    private JumpResponse evaluateJump(String shipId, String sector,
                                     EngineService.WarpStatusDTO engineStatus,
                                     RadarService.ScanResult radarResult,
                                     long elapsedTime) {

        // 1. 检查引擎状态
        if (!engineStatus.isWarpReady()) {
            return new JumpResponse(false,
                    "跃迁中止：引擎未就绪 - 核心温度 " + String.format("%.1f", engineStatus.getTemperature()) + "°C",
                    shipId, sector, engineStatus, radarResult, elapsedTime);
        }

        // 2. 检查威胁等级
        String threatLevel = radarResult.getThreatLevel();
        if ("CRITICAL".equals(threatLevel) || "HIGH".equals(threatLevel)) {
            return new JumpResponse(false,
                    "跃迁中止：目标星区威胁等级过高 - " + threatLevel + "，探测到 " + radarResult.getEnemyCount() + " 艘敌舰",
                    shipId, sector, engineStatus, radarResult, elapsedTime);
        }

        // 3. 跃迁获批
        String message = String.format("跃迁指令已批准 - 引擎温度 %.1f°C，敌舰 %d 艘，威胁等级 %s",
                engineStatus.getTemperature(), radarResult.getEnemyCount(), threatLevel);

        return new JumpResponse(true, message, shipId, sector, engineStatus, radarResult, elapsedTime);
    }

    /**
     * 获取遥测统计信息
     */
    @GetMapping("/telemetry/stats")
    public ResponseEntity<?> getTelemetryStats() {
        TelemetryService.TelemetryStats stats = telemetryService.getStats();

        Map<String, Object> result = new HashMap<>();
        result.put("totalScans", stats.getTotalScans());
        result.put("successfulScans", stats.getSuccessfulScans());
        result.put("failedScans", stats.getFailedScans());
        result.put("successRate", String.format("%.2f%%", stats.getSuccessRate()));

        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "fleet-command-gateway");
        return ResponseEntity.ok(health);
    }

    /**
     * 通用代理调用接口
     * 根据 serviceName 在 Spring 容器中找到对应的 @LuminaReference 代理对象
     * 利用反射调用该对象的 methodName，并传入 args
     * 这样可以确保所有请求都真正走过了 Consumer 端的 Mock 拦截器
     *
     * @param request 代理调用请求
     * @return 调用结果
     */
    @PostMapping("/proxy-invoke")
    public ResponseEntity<?> proxyInvoke(@RequestBody ProxyInvokeRequest request) {
        long startTime = System.currentTimeMillis();
        String serviceName = request.getServiceName();
        String methodName = request.getMethodName();
        Map<String, Object> params = request.getParams();

        // 兼容旧格式：如果没有 params 但有 args，则转换为 params
        if (params == null && request.getArgs() != null && request.getArgs().length > 0) {
            params = new HashMap<>();
            Object[] args = request.getArgs();
            for (int i = 0; i < args.length; i++) {
                params.put("arg" + i, args[i]);
            }
            log.info("🔌 [Proxy] 使用旧格式 args 转换: {}", params);
        }

        log.info("🔌 [Proxy] 收到通用调用请求 - Service: {}, Method: {}, Params: {}",
                serviceName, methodName, params);

        Map<String, Object> result = new HashMap<>();
        result.put("serviceName", serviceName);
        result.put("methodName", methodName);
        result.put("timestamp", System.currentTimeMillis());

        try {
            // 根据 serviceName 获取代理对象
            Object proxyObject = getProxyObject(serviceName);
            if (proxyObject == null) {
                log.error("❌ [Proxy] 未找到服务: {}", serviceName);
                result.put("success", false);
                result.put("error", "Service not found: " + serviceName);
                result.put("duration", System.currentTimeMillis() - startTime);
                return ResponseEntity.ok(result);
            }

            // 找到方法
            Method method = findMethod(proxyObject, methodName);
            if (method == null) {
                log.error("❌ [Proxy] 未找到方法: {}.{}", serviceName, methodName);
                result.put("success", false);
                result.put("error", "Method not found: " + methodName);
                result.put("duration", System.currentTimeMillis() - startTime);
                return ResponseEntity.ok(result);
            }

            // 构建参数数组（根据方法参数顺序从 Map 中提取）
            Object[] args = buildArgsFromParams(method, params);

            // 调用方法
            method.setAccessible(true);
            Object response = method.invoke(proxyObject, args);

            long duration = System.currentTimeMillis() - startTime;

            // 获取 Trace ID（必须在 clear 之前获取）
            String traceId = TraceContext.getTraceId();

            log.info("✅ [Proxy] 调用成功 - {}.{} 耗时: {}ms, TraceId: {}", serviceName, methodName, duration, traceId);

            result.put("success", true);
            result.put("data", response);
            result.put("duration", duration);
            result.put("traceId", traceId);

            // 清理 TraceContext（在获取 traceId 之后）
            TraceContext.clear();

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 获取 Trace ID
            String traceId = TraceContext.getTraceId();

            log.error("❌ [Proxy] 调用失败 - {}.{} 耗时: {}ms TraceId: {} 错误: {}",
                    serviceName, methodName, duration, traceId, e.getMessage(), e);

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("duration", duration);
            result.put("traceId", traceId);

            // 清理 TraceContext
            TraceContext.clear();

            return ResponseEntity.ok(result);
        }
    }

    /**
     * 根据服务名获取代理对象
     */
    private Object getProxyObject(String serviceName) {
        // 从客户端中获取 @LuminaReference 注入的代理对象
        if (serviceName.contains("engine") || serviceName.equals("lumina-rpc-engine")) {
            return engineServiceClient.getEngineService();
        }
        if (serviceName.contains("radar") || serviceName.equals("lumina-rpc-radar")) {
            return radarServiceClient.getRadarService();
        }
        // 尝试通过 Spring 容器查找
        return null;
    }

    /**
     * 查找方法（根据方法名）
     */
    private Method findMethod(Object proxyObject, String methodName) {
        if (proxyObject == null) return null;

        // 先尝试在接口中查找
        for (Class<?> iface : proxyObject.getClass().getInterfaces()) {
            try {
                return iface.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {}
        }

        // 尝试无参方法
        try {
            return proxyObject.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ignored) {}

        // 尝试查找所有方法
        for (Method m : proxyObject.getClass().getMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }

        return null;
    }

    /**
     * 根据方法参数定义，从 Map 中构建参数数组
     * 例如：方法定义 func(String sector, String shipId)
     * Map: {"sector": "Alpha-7", "shipId": "USS-1701"}
     * 结果: Object[] {"Alpha-7", "USS-1701"}
     */
    private Object[] buildArgsFromParams(Method method, Map<String, Object> params) {
        if (method == null) return new Object[0];

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        // 获取方法的参数名称
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            Object value = params != null ? params.get(paramName) : null;

            if (value == null && params != null) {
                // 尝试用 arg{i} 作为备用键名
                value = params.get("arg" + i);
            }

            // 类型转换
            if (value != null) {
                args[i] = convertToType(value, paramTypes[i]);
            } else {
                args[i] = null;
            }

            log.debug("🔧 [Proxy] 参数[{}] {} = {} (类型: {})",
                    i, paramName, args[i], paramTypes[i].getName());
        }

        return args;
    }

    /**
     * 类型转换：将输入值转换为目标参数类型
     */
    private Object convertToType(Object value, Class<?> targetType) {
        if (value == null) return null;

        // 如果可以直接赋值
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // String 转其他基本类型
        if (value instanceof String) {
            String strValue = (String) value;
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(strValue);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(strValue);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(strValue);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(strValue);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(strValue);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(strValue);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(strValue);
            }
            if (targetType == char.class || targetType == Character.class) {
                return strValue.length() > 0 ? strValue.charAt(0) : null;
            }
            return strValue;
        }

        // 其他类型转换尝试
        try {
            return targetType.getDeclaredConstructor(String.class).newInstance(value.toString());
        } catch (Exception ignored) {}

        return value;
    }

    /**
     * 通用代理调用请求
     */
    @Data
    public static class ProxyInvokeRequest {
        private String serviceName;
        private String methodName;
        private Object[] args;          // 兼容旧格式：数组
        private Map<String, Object> params; // 新格式：Map { "sector": "Alpha-7", "shipId": "USS-1701" }
    }

    /**
     * 手动扫描接口（诊断模式）
     * 手动触发一次 Engine 和 Radar 调用，用于测试 Mock 规则
     *
     * @param scanType 扫描类型：engine, radar, all
     * @param shipId 星舰ID（用于引擎扫描）
     * @param sector 星区（用于雷达扫描）
     * @return 扫描结果
     */
    @PostMapping("/manual-scan")
    public ResponseEntity<?> manualScan(
            @RequestParam(defaultValue = "all") String scanType,
            @RequestParam(defaultValue = "USS-ENTERPRISE-NCC-1701") String shipId,
            @RequestParam(defaultValue = "Alpha-7") String sector) {

        long startTime = System.currentTimeMillis();
        log.info("🔧 [Command] 收到手动扫描请求 - Type: {}, Ship: {}, Sector: {}", scanType, shipId, sector);

        Map<String, Object> result = new HashMap<>();
        result.put("scanType", scanType);
        result.put("shipId", shipId);
        result.put("sector", sector);
        result.put("timestamp", System.currentTimeMillis());

        try {
            // 引擎扫描
            if ("engine".equals(scanType) || "all".equals(scanType)) {
                long engineStart = System.currentTimeMillis();
                EngineService.WarpStatusDTO engineStatus = engineServiceClient.getEngineStatus(shipId);
                long engineElapsed = System.currentTimeMillis() - engineStart;

                Map<String, Object> engineResult = new HashMap<>();
                engineResult.put("status", engineStatus);
                engineResult.put("elapsedMs", engineElapsed);

                result.put("engine", engineResult);
                log.info("🔧 [Command] 引擎扫描完成 - Ship: {}, 耗时: {}ms", shipId, engineElapsed);
            }

            // 雷达扫描
            if ("radar".equals(scanType) || "all".equals(scanType)) {
                long radarStart = System.currentTimeMillis();
                RadarService.ScanResult radarResult = radarServiceClient.scanEnemies(sector);
                long radarElapsed = System.currentTimeMillis() - radarStart;

                Map<String, Object> radarResultMap = new HashMap<>();
                radarResultMap.put("status", radarResult);
                radarResultMap.put("elapsedMs", radarElapsed);

                result.put("radar", radarResultMap);
                log.info("🔧 [Command] 雷达扫描完成 - Sector: {}, 敌舰: {}, 耗时: {}ms",
                        sector, radarResult.getEnemyCount(), radarElapsed);
            }

            long totalElapsed = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("totalElapsedMs", totalElapsed);

            log.info("✅ [Command] 手动扫描完成 - Type: {}, 总耗时: {}ms", scanType, totalElapsed);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            // 增强容错：即使 RPC 报错也返回 200 OK，前端不会报 Network Error
            log.error("❌ [Command] 手动扫描失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            result.put("totalElapsedMs", System.currentTimeMillis() - startTime);
            // 关键修改：返回 200 OK 而不是 500，让前端能正常解析响应
            return ResponseEntity.ok(result);
        }
    }

    // ==================== DTO Classes ====================

    /**
     * 跃迁响应DTO
     */
    @Data
    public static class JumpResponse {
        /** 是否成功 */
        private boolean success;
        /** 消息 */
        private String message;
        /** 星舰ID */
        private String shipId;
        /** 目标星区 */
        private String sector;
        /** 引擎状态 */
        private EngineService.WarpStatusDTO engineStatus;
        /** 雷达扫描结果 */
        private RadarService.ScanResult radarResult;
        /** 总耗时 */
        private long elapsedTime;

        public JumpResponse() {
        }

        public JumpResponse(boolean success, String message, String shipId, String sector,
                           EngineService.WarpStatusDTO engineStatus, RadarService.ScanResult radarResult,
                           long elapsedTime) {
            this.success = success;
            this.message = message;
            this.shipId = shipId;
            this.sector = sector;
            this.engineStatus = engineStatus;
            this.radarResult = radarResult;
            this.elapsedTime = elapsedTime;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getShipId() {
            return shipId;
        }

        public void setShipId(String shipId) {
            this.shipId = shipId;
        }

        public String getSector() {
            return sector;
        }

        public void setSector(String sector) {
            this.sector = sector;
        }

        public EngineService.WarpStatusDTO getEngineStatus() {
            return engineStatus;
        }

        public void setEngineStatus(EngineService.WarpStatusDTO engineStatus) {
            this.engineStatus = engineStatus;
        }

        public RadarService.ScanResult getRadarResult() {
            return radarResult;
        }

        public void setRadarResult(RadarService.ScanResult radarResult) {
            this.radarResult = radarResult;
        }

        public long getElapsedTime() {
            return elapsedTime;
        }

        public void setElapsedTime(long elapsedTime) {
            this.elapsedTime = elapsedTime;
        }
    }
}
