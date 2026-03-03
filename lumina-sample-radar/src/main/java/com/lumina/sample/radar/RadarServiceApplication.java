package com.lumina.sample.radar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 深空雷达阵列服务 - 启动类
 * Starfleet Demo: Deep Space Radar Array Node (故障模拟节点)
 */
@SpringBootApplication(scanBasePackages = {"com.lumina.sample.radar", "com.lumina.rpc"})
public class RadarServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RadarServiceApplication.class, args);
        System.out.println("📡 [Starfleet] Deep Space Radar Array Online - Port 8082");
        System.out.println("   🎯 深空雷达阵列已启动 - ⚠️ 故障模拟节点 (40%概率离子风暴干扰)");
    }
}
