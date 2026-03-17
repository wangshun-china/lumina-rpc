package com.lumina.sample.engine.service;

import com.lumina.rpc.core.annotation.LuminaService;
import com.lumina.sample.engine.api.EngineService;
import com.lumina.sample.engine.api.EngineService.WarpStatusDTO;
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

    @Override
    public String testCluster(boolean shouldFail) {
        // 获取当前实例信息
        String port = System.getenv().getOrDefault("SERVER_PORT", "8081");
        String host = System.getenv().getOrDefault("HOSTNAME", "localhost");
        String instanceId = host + ":" + port;

        // 通过环境变量控制该实例是否模拟故障
        // 启动时设置: SIMULATE_FAILURE=true
        boolean simulateFailure = Boolean.parseBoolean(
            System.getenv().getOrDefault("SIMULATE_FAILURE", "false")
        );

        // 实例级别的故障模拟（优先级高于参数）
        if (simulateFailure) {
            log.warn("💥 [Engine-Test] Instance {} is configured to fail (SIMULATE_FAILURE=true)", instanceId);
            throw new RuntimeException("Simulated failure from instance " + instanceId);
        }

        // 参数级别的故障模拟（用于测试 Failfast/Failsafe/Forking）
        if (shouldFail) {
            log.warn("💥 [Engine-Test] Simulating failure on instance {} (shouldFail=true)", instanceId);
            throw new RuntimeException("Simulated failure from instance " + instanceId);
        }

        String result = "Success from Engine instance [" + instanceId + "]";
        log.info("✅ [Engine-Test] {}", result);
        return result;
    }
}
