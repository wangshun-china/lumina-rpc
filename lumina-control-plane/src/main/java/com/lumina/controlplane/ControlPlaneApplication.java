package com.lumina.controlplane;

import com.lumina.controlplane.service.SseBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Control Plane 启动类
 * 负责管理 Mock Rule 并通过 SSE 广播变更通知
 *
 * 注意：不再依赖 lumina-rpc-core，只依赖纯净的 lumina-rpc-protocol
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.lumina.controlplane")
public class ControlPlaneApplication {

    private static final Logger logger = LoggerFactory.getLogger(ControlPlaneApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ControlPlaneApplication.class, args);

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, closing SSE connections...");
            SseBroadcastService broadcastService = context.getBean(SseBroadcastService.class);
            broadcastService.shutdown();
        }));

        logger.info("========================================================");
        logger.info("Lumina Control Plane started successfully!");
        logger.info("API Base URL: http://localhost:8080/api/v1");
        logger.info("SSE Subscribe: http://localhost:8080/api/v1/sse/subscribe/{serviceName}");
        logger.info("H2 Console: http://localhost:8080/h2-console");
        logger.info("========================================================");
    }
}
