package com.lumina.sample.engine.service;

import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 曲率引擎服务接口
 * 模拟深空通信的高延迟特性
 */
@Service
public interface EngineService {

    /**
     * 获取曲率引擎状态
     * @param shipId 星舰ID
     * @return 引擎状态DTO
     */
    WarpStatusDTO getWarpStatus(String shipId);

    /**
     * 测试方法：可模拟失败
     * 用于测试集群容错策略
     *
     * @param shouldFail 是否模拟失败
     * @return 测试结果
     */
    String testCluster(boolean shouldFail);

    /**
     * 引擎状态DTO
     */
    @Data
    class WarpStatusDTO {
        /** 星舰ID */
        private String shipId;
        /** 引擎核心温度 (摄氏度) */
        private double temperature;
        /** 是否可进行曲率跃迁 */
        private boolean warpReady;
        /** 当前曲率等级 (0-9) */
        private int warpFactor;
        /** 引擎状态描述 */
        private String status;
        /** 响应延迟 (ms) */
        private long responseDelay;
    }
}
