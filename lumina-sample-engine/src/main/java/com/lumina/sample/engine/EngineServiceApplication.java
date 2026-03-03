package com.lumina.sample.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 曲率引擎传感器服务 - 启动类
 * Starfleet Demo: Warp Engine Sensor Node
 */
@SpringBootApplication(scanBasePackages = {"com.lumina.sample.engine", "com.lumina.rpc"})
public class EngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineServiceApplication.class, args);
        System.out.println("✨ [Starfleet] Warp Engine Sensor Online - Port 8081");
        System.out.println("   🚀 曲率引擎传感器已启动 - 模拟深空高延迟通信");
    }
}
