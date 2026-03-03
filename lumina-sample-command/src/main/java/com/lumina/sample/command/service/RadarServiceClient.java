package com.lumina.sample.command.service;

import com.lumina.rpc.core.annotation.LuminaReference;
import com.lumina.sample.radar.service.RadarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 深空雷达服务客户端
 * 通过 @LuminaReference 注入 RadarService 的 RPC 代理
 * 注意：RadarService 是故障模拟节点，40%概率抛出异常
 */
@Slf4j
@Component
public class RadarServiceClient {

    /**
     * 深空雷达服务 RPC 代理
     * 配置：超时5秒，重试3次
     */
    @LuminaReference(interfaceClass = RadarService.class, timeout = 5000, retries = 3)
    private RadarService radarService;

    /**
     * 扫描指定星区的敌对目标
     * 注意：此方法可能抛出异常（离子风暴干扰）
     *
     * @param sector 星区编号 (如 "Alpha-7", "Beta-12")
     * @return 扫描结果（包含敌舰数量、威胁等级等）
     * @throws RuntimeException 当雷达受到离子风暴干扰时抛出
     */
    public RadarService.ScanResult scanEnemies(String sector) {
        try {
            log.debug("[Command] 请求雷达扫描 - Sector: {}", sector);
            RadarService.ScanResult result = radarService.scanEnemies(sector);
            log.debug("[Command] 雷达扫描响应 - 敌舰: {}, 威胁等级: {}",
                    result.getEnemyCount(), result.getThreatLevel());
            return result;
        } catch (Exception e) {
            log.error("[Command] 雷达扫描失败 - Sector: {}, 错误: {}", sector, e.getMessage());
            throw e;
        }
    }

    /**
     * 获取原始 RadarService 代理（用于直接调用）
     *
     * @return RadarService 代理
     */
    public RadarService getRadarService() {
        return radarService;
    }
}
