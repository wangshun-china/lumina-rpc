package com.lumina.sample.signal.service;

import com.lumina.rpc.core.annotation.LuminaService;
import com.lumina.sample.signal.api.SignalAnalysisService;
import com.lumina.sample.signal.api.SignalAnalysisService.SignalAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 信号分析服务实现。
 *
 * 该服务故意保持轻量，用于让 demo 链路多一跳：Command -> Radar -> Signal。
 */
@Slf4j
@Service
@LuminaService
public class SignalAnalysisServiceImpl implements SignalAnalysisService {

    private final Random random = new Random();

    @Override
    public SignalAnalysis analyzeSector(String sector, int contactCount) {
        int signalStrength = 45 + random.nextInt(56);
        String interferenceLevel = resolveInterference(signalStrength);
        String recommendedThreatLevel = calibrateThreat(contactCount, interferenceLevel);
        double confidence = Math.round((signalStrength * 0.75 + 20) * 10.0) / 10.0;

        String host = System.getenv().getOrDefault("HOSTNAME", "localhost");

        SignalAnalysis analysis = new SignalAnalysis();
        analysis.setSector(sector);
        analysis.setSignalStrength(signalStrength);
        analysis.setInterferenceLevel(interferenceLevel);
        analysis.setRecommendedThreatLevel(recommendedThreatLevel);
        analysis.setConfidence(Math.min(99.9, confidence));
        analysis.setAnalyzerNode(host);
        analysis.setNotes("Signal calibrated from " + contactCount + " radar contacts");

        log.info("📶 [Signal] 星区 {} 信号分析完成 | contacts={} | strength={} | interference={} | threat={}",
                sector, contactCount, signalStrength, interferenceLevel, recommendedThreatLevel);

        return analysis;
    }

    private String resolveInterference(int signalStrength) {
        if (signalStrength < 55) {
            return "JAMMED";
        }
        if (signalStrength < 75) {
            return "NOISY";
        }
        return "CLEAR";
    }

    private String calibrateThreat(int contactCount, String interferenceLevel) {
        if (contactCount == 0) {
            return "NONE";
        }
        if ("JAMMED".equals(interferenceLevel) && contactCount >= 3) {
            return "HIGH";
        }
        if (contactCount <= 2) {
            return "LOW";
        }
        if (contactCount <= 4) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
