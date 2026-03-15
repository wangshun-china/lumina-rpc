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

    /** 配置缓存（用于快速查询） */
    private final Map<String, ProtectionConfigEntity> configCache = new ConcurrentHashMap<>();

    /** 配置版本号（用于检测更新） */
    private volatile long globalVersion = System.currentTimeMillis();

    // ==================== 运行时统计数据 ====================

    /** 限流器通过数统计 */
    private final Map<String, Long> passedStats = new ConcurrentHashMap<>();

    /** 限流器拒绝数统计 */
    private final Map<String, Long> rejectedStats = new ConcurrentHashMap<>();

    /** 熔断器状态 */
    private final Map<String, String> circuitBreakerStates = new ConcurrentHashMap<>();

    /** 总通过数 */
    private volatile long totalPassed = 0L;

    /** 总拒绝数 */
    private volatile long totalRejected = 0L;

    public ProtectionConfigService(ProtectionConfigRepository repository) {
        this.repository = repository;
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
     * 根据 serviceName 获取配置（优先从缓存，带运行时统计）
     */
    public ProtectionConfigEntity findByServiceName(String serviceName) {
        // 先查缓存
        ProtectionConfigEntity cached = configCache.get(serviceName);
        ProtectionConfigEntity config;
        if (cached != null) {
            config = cached;
        } else {
            // 缓存没有则查数据库
            config = repository.findByServiceName(serviceName).orElse(null);
        }

        // 如果找到配置，附加统计数据
        if (config != null) {
            config.setRateLimiterPassed(passedStats.getOrDefault(serviceName, 0L));
            config.setRateLimiterRejected(rejectedStats.getOrDefault(serviceName, 0L));
            config.setCircuitBreakerState(circuitBreakerStates.getOrDefault(serviceName, "CLOSED"));
        }

        return config;
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
        return saved;
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

    /**
     * 更新统计数据（Consumer 上报）
     */
    public void updateStats(String serviceName, Long passed, Long rejected, String circuitBreakerState) {
        passedStats.put(serviceName, passed);
        rejectedStats.put(serviceName, rejected);
        circuitBreakerStates.put(serviceName, circuitBreakerState);

        // 重新计算总数
        totalPassed = passedStats.values().stream().mapToLong(Long::longValue).sum();
        totalRejected = rejectedStats.values().stream().mapToLong(Long::longValue).sum();

        logger.debug("Updated stats for {}: passed={}, rejected={}, cbState={}",
                serviceName, passed, rejected, circuitBreakerState);
    }

    /**
     * 获取总通过数
     */
    public long getTotalPassed() {
        return totalPassed;
    }

    /**
     * 获取总拒绝数
     */
    public long getTotalRejected() {
        return totalRejected;
    }

    /**
     * 获取服务通过数
     */
    public long getPassed(String serviceName) {
        return passedStats.getOrDefault(serviceName, 0L);
    }

    /**
     * 获取服务拒绝数
     */
    public long getRejected(String serviceName) {
        return rejectedStats.getOrDefault(serviceName, 0L);
    }
}