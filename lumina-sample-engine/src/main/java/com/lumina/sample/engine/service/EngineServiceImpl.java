package com.lumina.sample.engine.service;


import com.lumina.rpc.core.annotation.LuminaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 曲率引擎服务实现
 * 诊断模式：移除所有延迟和混沌逻辑，秒回100%成功
 */
@Slf4j
@Service
@LuminaService
public class EngineServiceImpl implements EngineService {

    private final Random random = new Random();

    @Override
    public WarpStatusDTO getWarpStatus(String shipId) {
        // 诊断模式：移除延迟，秒回数据

        // 生成模拟引擎数据
        double temperature = 2800 + random.nextDouble() * 400; // 2800-3200K
        boolean warpReady = temperature < 3100;
        int warpFactor = warpReady ? random.nextInt(10) : 0;

        String status;
        if (temperature > 3150) {
            status = "⚠️ 核心过热 - 建议降速";
        } else if (warpReady) {
            status = "✅ 引擎就绪 - 可以进行曲率跃迁";
        } else {
            status = "⏳ 引擎预热中...";
        }

        WarpStatusDTO dto = new WarpStatusDTO();
        dto.setShipId(shipId);
        dto.setTemperature(Math.round(temperature * 10.0) / 10.0);
        dto.setWarpReady(warpReady);
        dto.setWarpFactor(warpFactor);
        dto.setStatus(status);
        dto.setResponseDelay(0); // 诊断模式：无延迟

        log.info("🚀 [Engine] Ship {} queried warp status | Temp: {}K | Ready: {} | Delay: {}ms",
                shipId, dto.getTemperature(), warpReady, 0);

        return dto;
    }
}
