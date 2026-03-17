package com.lumina.sample.radar.api;

import lombok.Data;
import java.util.List;

/**
 * 深空雷达服务接口
 * 扫描周边敌对目标
 *
 * 注意：此接口位于 -api 模块，Consumer 只需依赖此模块即可调用服务
 */
public interface RadarService {

    /**
     * 扫描指定星区的敌对目标
     * @param sector 星区编号 (如 "Alpha-7", "Beta-12")
     * @return 扫描结果DTO
     */
    ScanResult scanEnemies(String sector);

    /**
     * 扫描结果DTO
     */
    @Data
    class ScanResult {
        /** 扫描的星区 */
        private String sector;
        /** 发现的敌舰数量 */
        private int enemyCount;
        /** 威胁等级 (LOW/MEDIUM/HIGH/CRITICAL) */
        private String threatLevel;
        /** 扫描状态 */
        private String status;
        /** 扫描耗时 (ms) */
        private long scanTime;
        /** 详细敌舰信息 */
        private List<EnemyContact> contacts;
    }

    /**
     * 敌舰接触信息
     */
    @Data
    class EnemyContact {
        /** 敌舰ID */
        private String shipId;
        /** 敌舰类型 */
        private String shipClass;
        /** 距离 (光秒) */
        private double distance;
        /** 航向 */
        private String heading;
        /** 威胁评估 */
        private String threatAssessment;
    }
}
