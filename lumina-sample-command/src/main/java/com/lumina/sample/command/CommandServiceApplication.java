package com.lumina.sample.command;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 舰队指挥网关服务 - 启动类
 * Starfleet Demo: Fleet Command Gateway (Consumer/BFF)
 */
@SpringBootApplication(scanBasePackages = {"com.lumina.sample.command", "com.lumina.rpc"})
@EnableScheduling
public class CommandServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(CommandServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CommandServiceApplication.class, args);
        logger.info("🎯 [Starfleet] Fleet Command Center Online - Port 8083");
        logger.info("⚡ 舰队指挥网关已启动 - 自动遥测引擎运行中 (每3秒扫描)");
        logger.info("🔗 依赖服务: EngineService (曲率引擎) + RadarService (深空雷达，内部依赖 SignalAnalysisService)");
    }
}
