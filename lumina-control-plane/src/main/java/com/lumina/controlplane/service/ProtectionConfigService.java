package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.lumina.controlplane.repository.ProtectionConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保护配置服务
 *
 * 管理熔断器和限流器的动态配置
 */
@Service
public class ProtectionConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigService.class);

    private final ProtectionConfigRepository repository;
    private final SseBroadcastService sseBroadcastService;

    /** 配置缓存（用于快速查询） */
    private final Map<String, ProtectionConfigEntity> configCache = new ConcurrentHashMap<>();

    /** 配置版本号（用于检测更新） */
    private volatile long globalVersion = System.currentTimeMillis();

    public ProtectionConfigService(ProtectionConfigRepository repository,
                                   SseBroadcastService sseBroadcastService) {
        this.repository = repository;
        this.sseBroadcastService = sseBroadcastService;
        loadAllConfigs();
    }

    /**
     * 加载所有配置到缓存
     */
    private void loadAllConfigs() {
        List<ProtectionConfigEntity> configs = repository.findAll();
        configCache.clear();
        for (ProtectionConfigEntity config : configs) {
            configCache.put(config.getServiceName(), config);
        }
        logger.info("Loaded {} protection configs into cache", configs.size());
    }

    /**
     * 获取所有配置
     */
    public List<ProtectionConfigEntity> findAll() {
        return repository.findAll();
    }

    /**
     * 根据 serviceName 获取配置（优先从缓存）
     */
    public ProtectionConfigEntity findByServiceName(String serviceName) {
        ProtectionConfigEntity cached = configCache.get(serviceName);
        if (cached != null) {
            return cached;
        }
        return repository.findByServiceName(serviceName).orElse(null);
    }

    /**
     * 获取或创建默认配置
     */
    public ProtectionConfigEntity getOrCreateDefault(String serviceName) {
        ProtectionConfigEntity config = findByServiceName(serviceName);
        if (config == null) {
            config = new ProtectionConfigEntity();
            config.setServiceName(serviceName);
            config = repository.save(config);
            configCache.put(serviceName, config);
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
        configCache.put(saved.getServiceName(), saved);
        globalVersion = System.currentTimeMillis();
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
            // 不影响主流程，仅记录日志
        }
    }

    /**
     * 批量保存配置
     */
    @Transactional
    public List<ProtectionConfigEntity> saveAll(List<ProtectionConfigEntity> configs) {
        List<ProtectionConfigEntity> saved = repository.saveAll(configs);
        for (ProtectionConfigEntity config : saved) {
            configCache.put(config.getServiceName(), config);
        }
        globalVersion = System.currentTimeMillis();
        logger.info("Saved {} protection configs", saved.size());
        return saved;
    }

    /**
     * 删除配置
     */
    @Transactional
    public void delete(String serviceName) {
        repository.deleteByServiceName(serviceName);
        configCache.remove(serviceName);
        globalVersion = System.currentTimeMillis();
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

    /**
     * 获取全局版本号（用于检测配置变更）
     */
    public long getGlobalVersion() {
        return globalVersion;
    }

    /**
     * 刷新缓存
     */
    public void refreshCache() {
        loadAllConfigs();
        globalVersion = System.currentTimeMillis();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return configCache.size();
    }
}