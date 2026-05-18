package com.lumina.sample.signal.api;

import lombok.Data;

/**
 * 信号分析服务接口。
 *
 * RadarService 会调用该服务对原始扫描结果做二次校准，用于演示多级 RPC：
 * Command -> Radar -> SignalAnalysis。
 */
public interface SignalAnalysisService {

    /**
     * 分析指定星区的电磁信号和干扰强度。
     *
     * @param sector 星区编号
     * @param contactCount 雷达已发现的目标数量
     * @return 信号分析结果
     */
    SignalAnalysis analyzeSector(String sector, int contactCount);

    @Data
    class SignalAnalysis {
        /** 分析的星区 */
        private String sector;
        /** 信号强度，0-100 */
        private int signalStrength;
        /** 干扰等级：CLEAR / NOISY / JAMMED */
        private String interferenceLevel;
        /** 信号校准后的建议威胁等级 */
        private String recommendedThreatLevel;
        /** 分析置信度，0-100 */
        private double confidence;
        /** 执行分析的节点标识 */
        private String analyzerNode;
        /** 分析说明 */
        private String notes;
    }
}
