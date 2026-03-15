package com.lumina.sample.command.service;

import com.lumina.rpc.core.annotation.LuminaReference;
import com.lumina.sample.engine.service.EngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 集群容错策略测试客户端
 *
 * 使用不同的集群策略注入同一个服务，用于对比测试
 */
@Slf4j
@Component
public class ClusterTestClient {

    // ==================== 四种集群策略 ====================

    /**
     * Failover 策略（默认）- 失败自动重试其他服务器
     */
    @LuminaReference(
        interfaceClass = EngineService.class,
        cluster = "failover",
        retries = 3,
        timeout = 5000
    )
    private EngineService failoverClient;

    /**
     * Failfast 策略 - 快速失败，只发起一次调用
     */
    @LuminaReference(
        interfaceClass = EngineService.class,
        cluster = "failfast",
        timeout = 5000
    )
    private EngineService failfastClient;

    /**
     * Failsafe 策略 - 失败安全，异常直接忽略
     */
    @LuminaReference(
        interfaceClass = EngineService.class,
        cluster = "failsafe",
        timeout = 5000
    )
    private EngineService failsafeClient;

    /**
     * Forking 策略 - 并行调用多个服务器，一个成功即返回
     */
    @LuminaReference(
        interfaceClass = EngineService.class,
        cluster = "forking",
        timeout = 5000
    )
    private EngineService forkingClient;

    // ==================== Getter 方法 ====================

    public EngineService getFailoverClient() {
        return failoverClient;
    }

    public EngineService getFailfastClient() {
        return failfastClient;
    }

    public EngineService getFailsafeClient() {
        return failsafeClient;
    }

    public EngineService getForkingClient() {
        return forkingClient;
    }
}