package com.lumina.sample.command.service;

import com.lumina.rpc.core.annotation.LuminaReference;
import com.lumina.sample.engine.api.EngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 曲率引擎服务客户端
 * 通过 @LuminaReference 注入 EngineService 的 RPC 代理
 */
@Slf4j
@Component
public class EngineServiceClient {

    /**
     * 曲率引擎服务 RPC 代理
     */
    @LuminaReference(interfaceClass = EngineService.class, timeout = 5000, retries = 3)
    private EngineService engineService;

    /**
     * 获取引擎状态
     *
     * @param shipId 星舰IDA
     * @return 引擎状态 DTO
     */
    public EngineService.WarpStatusDTO getEngineStatus(String shipId) {
        try {
            log.debug("[Command] 请求引擎状态 - ShipId: {}", shipId);
            EngineService.WarpStatusDTO status = engineService.getWarpStatus(shipId);
            log.debug("[Command] 引擎状态响应 - 温度: {}, 跃迁就绪: {}",
                    status.getTemperature(), status.isWarpReady());
            return status;
        } catch (Exception e) {
            log.error("[Command] 获取引擎状态失败 - ShipId: {}, 错误: {}", shipId, e.getMessage());
            throw e;
        }
    }

    /**
     * 获取原始 EngineService 代理（用于直接调用）
     *
     * @return EngineService 代理
     */
    public EngineService getEngineService() {
        return engineService;
    }
}
