package com.lumina.sample.command.service;

import com.lumina.sample.engine.service.EngineService;
import com.lumina.sample.radar.service.RadarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 遥测数据采集服务
 * 诊断模式：停用所有 @Scheduled 定时任务，改为手动触发
 */
@Slf4j
@Service
public class TelemetryService {

    private final EngineServiceClient engineServiceClient;
    private final RadarServiceClient radarServiceClient;

    // 统计计数器
    private final AtomicLong scanCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    // 默认扫描的星舰和星区
    private static final String DEFAULT_SHIP_ID = "USS-ENTERPRISE-NCC-1701";
    private static final String DEFAULT_SECTOR = "Alpha-7";

    public TelemetryService(EngineServiceClient engineServiceClient,
                          RadarServiceClient radarServiceClient) {
        this.engineServiceClient = engineServiceClient;
        this.radarServiceClient = radarServiceClient;
    }

    /**
     * 手动遥测扫描任务（诊断模式：不再自动执行）
     * 供 CommandController 手动调用
     */
    public ManualScanResult manualScan(String shipId, String sector) {
        long startTime = System.currentTimeMillis();
        long scanId = scanCount.incrementAndGet();

        try {
            log.info("[Command] 手动扫描 #{} - Ship: {}, Sector: {}", scanId, shipId, sector);

            // 1. 查询曲率引擎状态
            EngineService.WarpStatusDTO engineStatus = engineServiceClient.getEngineStatus(shipId);
            double temperature = engineStatus.getTemperature();
            boolean warpReady = engineStatus.isWarpReady();

            // 2. 查询深空雷达扫描结果
            RadarService.ScanResult radarResult = radarServiceClient.scanEnemies(sector);
            int enemyCount = radarResult.getEnemyCount();
            String threatLevel = radarResult.getThreatLevel();

            // 3. 计算总耗时
            long elapsedTime = System.currentTimeMillis() - startTime;

            // 4. 统计
            successCount.incrementAndGet();

            String warpStatus = warpReady ? "✅ 可跃迁" : "❌ 未就绪";

            log.info("📡 [Command Center] 手动扫描 #{} -> 引擎温度: {}°C, 跃迁就绪: {}, 敌舰数量: {}, 威胁等级: {}, 耗时: {} ms",
                    scanId,
                    String.format("%.1f", temperature),
                    warpStatus,
                    enemyCount,
                    threatLevel,
                    elapsedTime);

            return new ManualScanResult(true, engineStatus, radarResult, elapsedTime, null);

        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("💥 [Command Center] 手动扫描 #{} 执行失败: {}", scanId, e.getMessage(), e);
            return new ManualScanResult(false, null, null, System.currentTimeMillis() - startTime, e.getMessage());
        }
    }

    /**
     * 手动扫描结果
     */
    public static class ManualScanResult {
        private final boolean success;
        private final EngineService.WarpStatusDTO engineStatus;
        private final RadarService.ScanResult radarResult;
        private final long elapsedTime;
        private final String errorMessage;

        public ManualScanResult(boolean success, EngineService.WarpStatusDTO engineStatus,
                               RadarService.ScanResult radarResult, long elapsedTime, String errorMessage) {
            this.success = success;
            this.engineStatus = engineStatus;
            this.radarResult = radarResult;
            this.elapsedTime = elapsedTime;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public EngineService.WarpStatusDTO getEngineStatus() { return engineStatus; }
        public RadarService.ScanResult getRadarResult() { return radarResult; }
        public long getElapsedTime() { return elapsedTime; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 获取统计信息
     */
    public TelemetryStats getStats() {
        return new TelemetryStats(scanCount.get(), successCount.get(), failureCount.get());
    }

    /**
     * 遥测统计信息
     */
    public static class TelemetryStats {
        private final long totalScans;
        private final long successfulScans;
        private final long failedScans;

        public TelemetryStats(long totalScans, long successfulScans, long failedScans) {
            this.totalScans = totalScans;
            this.successfulScans = successfulScans;
            this.failedScans = failedScans;
        }

        public long getTotalScans() {
            return totalScans;
        }

        public long getSuccessfulScans() {
            return successfulScans;
        }

        public long getFailedScans() {
            return failedScans;
        }

        public double getSuccessRate() {
            if (totalScans == 0) return 0.0;
            return (double) successfulScans / totalScans * 100;
        }
    }
}
