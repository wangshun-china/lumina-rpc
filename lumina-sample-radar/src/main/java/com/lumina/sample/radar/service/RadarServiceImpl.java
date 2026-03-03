package com.lumina.sample.radar.service;

import com.lumina.rpc.core.annotation.LuminaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 深空雷达服务实现
 * 诊断模式：移除所有混沌逻辑，秒回100%成功
 */
@Slf4j
@Service
@LuminaService
public class RadarServiceImpl implements RadarService {

    private final Random random = new Random();

    @Override
    public ScanResult scanEnemies(String sector) {
        long startTime = System.currentTimeMillis();

        // 诊断模式：移除 40% 离子风暴干扰，所有请求100%成功

        // 生成随机敌舰数据
        int enemyCount = random.nextInt(6); // 0-5 艘敌舰
        String threatLevel = calculateThreatLevel(enemyCount);

        List<EnemyContact> contacts = new ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            contacts.add(generateEnemyContact(i));
        }

        long scanTime = System.currentTimeMillis() - startTime;

        ScanResult result = new ScanResult();
        result.setSector(sector);
        result.setEnemyCount(enemyCount);
        result.setThreatLevel(threatLevel);
        result.setStatus("SCAN_COMPLETE");
        result.setScanTime(scanTime);
        result.setContacts(contacts);

        log.info("📡 [Radar] 星区 {} 扫描完成 | 敌舰: {} | 威胁等级: {} | 耗时: {}ms",
                sector, enemyCount, threatLevel, scanTime);

        return result;
    }

    /**
     * 计算威胁等级
     */
    private String calculateThreatLevel(int enemyCount) {
        if (enemyCount == 0) return "NONE";
        if (enemyCount <= 2) return "LOW";
        if (enemyCount <= 4) return "MEDIUM";
        return "HIGH";
    }

    /**
     * 生成敌舰接触信息
     */
    private EnemyContact generateEnemyContact(int index) {
        String[] shipClasses = {"Scout", "Frigate", "Destroyer", "Cruiser", "Battleship"};
        String[] assessments = {"Minimal threat", "Caution advised", "Hostile intent detected", "Imminent danger"};

        EnemyContact contact = new EnemyContact();
        contact.setShipId("ENEMY-" + (1000 + random.nextInt(9000)));
        contact.setShipClass(shipClasses[random.nextInt(shipClasses.length)]);
        contact.setDistance(0.5 + random.nextDouble() * 9.5); // 0.5 - 10 光秒
        contact.setHeading(random.nextInt(360) + "°");
        contact.setThreatAssessment(assessments[random.nextInt(assessments.length)]);

        return contact;
    }
}
