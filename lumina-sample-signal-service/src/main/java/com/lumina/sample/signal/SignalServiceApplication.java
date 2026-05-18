package com.lumina.sample.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 信号分析服务 - 启动类。
 */
@SpringBootApplication(scanBasePackages = {"com.lumina.sample.signal", "com.lumina.rpc"})
public class SignalServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(SignalServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SignalServiceApplication.class, args);
        logger.info("📶 [Starfleet] Signal Analysis Node Online - Port 8084");
        logger.info("🧭 信号分析节点已启动 - Radar 的下游 RPC 依赖");
    }
}
