package com.lumina.sample.command;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 舰队指挥网关服务 - 启动类
 * Starfleet Demo: Fleet Command Gateway (Consumer/BFF)
 */
@SpringBootApplication(scanBasePackages = {"com.lumina.sample.command", "com.lumina.rpc"})
@EnableScheduling
public class CommandServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommandServiceApplication.class, args);
        System.out.println("🎯 [Starfleet] Fleet Command Center Online - Port 8083");
        System.out.println("   ⚡ 舰队指挥网关已启动 - 自动遥测引擎运行中 (每3秒扫描)");
        System.out.println("   🔗 依赖服务: EngineService (曲率引擎) + RadarService (深空雷达)");
    }
}
