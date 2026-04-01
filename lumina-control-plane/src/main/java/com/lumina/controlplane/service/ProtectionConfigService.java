package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.lumina.controlplane.repository.ProtectionConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 保护配置服务（简化版，对标 Dubbo）
 *
 * 管理熔断器和限流器的动态配置
 * 移除内存缓存，直接查数据库（配置数量有限，性能足够）
 * 移除版本号机制，SSE 推送已足够可靠
 */
@Service
public class ProtectionConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigService.class);

    private final ProtectionConfigRepository repository;
    private final SseBroadcastService sseBroadcastService;

    public ProtectionConfigService(ProtectionConfigRepository repository,
                                   SseBroadcastService sseBroadcastService) {
        this.repository = repository;
        this.sseBroadcastService = sseBroadcastService;
        logger.info("ProtectionConfigService initialized (no cache, direct DB access)");
    }

    /**
     * 获取所有配置
     */
    public List<ProtectionConfigEntity> findAll() {
        return repository.findAll();
    }

    /**
     * 根据 serviceName 获取配置
     */
    public ProtectionConfigEntity findByServiceName(String serviceName) {
        return repository.findByServiceName(serviceName).orElse(null);
    }

    /**
     * 获取或创建默认配置
     */
    public ProtectionConfigEntity getOrCreateDefault(String serviceName) {
        ProtectionConfigEntity config = repository.findByServiceName(serviceName).orElse(null);
        if (config == null) {
            config = new ProtectionConfigEntity();
            config.setServiceName(serviceName);
            config = repository.save(config);
            logger.info("Created default protection config for service: {}", serviceName);
        }
        return config;
    }

    /**
     * 保存配置
     */
    @Transactional
    public ProtectionConfigEntity save(ProtectionConfigEntity config) {
        ProtectionConfigEntity saved = repository.save(config);
        logger.info("Saved protection config for service: {}", saved.getServiceName());

        // 广播配置变更事件（SSE实时推送）
        broadcastConfigChange(saved);

        return saved;
    }

    /**
     * 广播配置变更事件
     */
    private void broadcastConfigChange(ProtectionConfigEntity config) {
        try {
            if (sseBroadcastService != null) {
                sseBroadcastService.broadcastConfigChange("protection", config.getServiceName(), config);
                logger.debug("Broadcasted protection config change for service: {}", config.getServiceName());
            }
        } catch (Exception e) {
            logger.warn("Failed to broadcast config change: {}", e.getMessage());
        }
    }

    /**
     * 批量保存配置
     */
    @Transactional
    public List<ProtectionConfigEntity> saveAll(List<ProtectionConfigEntity> configs) {
        List<ProtectionConfigEntity> saved = repository.saveAll(configs);
        logger.info("Saved {} protection configs", saved.size());
        return saved;
    }

    /**
     * 删除配置
     */
    @Transactional
    public void delete(String serviceName) {
        repository.deleteByServiceName(serviceName);
        logger.info("Deleted protection config for service: {}", serviceName);
    }

    /**
     * 更新熔断器配置
     */
    @Transactional
    public ProtectionConfigEntity updateCircuitBreakerConfig(
            String serviceName,
            Boolean enabled,
            Integer threshold,
            Long timeout) {

        ProtectionConfigEntity config = getOrCreateDefault(serviceName);

        if (enabled != null) {
            config.setCircuitBreakerEnabled(enabled);
        }
        if (threshold != null) {
            config.setCircuitBreakerThreshold(threshold);
        }
        if (timeout != null) {
            config.setCircuitBreakerTimeout(timeout);
        }

        return save(config);
    }

    /**
     * 更新限流器配置
     */
    @Transactional
    public ProtectionConfigEntity updateRateLimiterConfig(
            String serviceName,
            Boolean enabled,
            Integer permits) {

        ProtectionConfigEntity config = getOrCreateDefault(serviceName);

        if (enabled != null) {
            config.setRateLimiterEnabled(enabled);
        }
        if (permits != null) {
            config.setRateLimiterPermits(permits);
        }

        return save(config);
    }

    /**
     * 更新集群配置（timeout、retries、clusterStrategy）
     */
    @Transactional
    public ProtectionConfigEntity updateClusterConfig(
            String serviceName,
            Long timeoutMs,
            Integer retries,
            String clusterStrategy) {

        ProtectionConfigEntity config = getOrCreateDefault(serviceName);

        if (timeoutMs != null) {
            config.setTimeoutMs(timeoutMs);
        }
        if (retries != null) {
            config.setRetries(retries);
        }
        if (clusterStrategy != null && !clusterStrategy.isEmpty()) {
            config.setClusterStrategy(clusterStrategy);
        }

        logger.info("Updated cluster config for {}: timeout={}ms, retries={}, strategy={}",
                serviceName, timeoutMs, retries, clusterStrategy);

        return save(config);
    }
}