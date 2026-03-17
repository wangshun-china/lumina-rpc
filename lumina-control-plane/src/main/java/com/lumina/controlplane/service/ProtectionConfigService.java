package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.lumina.controlplane.entity.ProtectionStatsEntity;
import com.lumina.controlplane.repository.ProtectionConfigRepository;
import com.lumina.controlplane.repository.ProtectionStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保护配置服务
 *
 * 管理熔断器和限流器的动态配置
 * 统计数据持久化存储，解决服务重启后丢失的问题
 */
@Service
public class ProtectionConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigService.class);

    private final ProtectionConfigRepository repository;
    private final ProtectionStatsRepository statsRepository;

    /** 配置缓存（用于快速查询） */
    private final Map<String, ProtectionConfigEntity> configCache = new ConcurrentHashMap<>();

    /** 配置版本号（用于检测更新） */
    private volatile long globalVersion = System.currentTimeMillis();

    public ProtectionConfigService(ProtectionConfigRepository repository, ProtectionStatsRepository statsRepository) {
        this.repository = repository;
        this.statsRepository = statsRepository;
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
     * 获取或创建统计数据实体
     */
    private ProtectionStatsEntity getOrCreateStats(String serviceName) {
        return statsRepository.findByServiceName(serviceName)
                .orElseGet(() -> {
                    ProtectionStatsEntity stats = new ProtectionStatsEntity();
                    stats.setServiceName(serviceName);
                    return statsRepository.save(stats);
                });
    }

    /**
     * 获取所有配置（带运行时统计）
     */
    public List<ProtectionConfigEntity> findAll() {
        List<ProtectionConfigEntity> configs = repository.findAll();
        // 为每个配置附加运行时统计数据（从持久化存储读取）
        for (ProtectionConfigEntity config : configs) {
            String serviceName = config.getServiceName();
            statsRepository.findByServiceName(serviceName).ifPresent(stats -> {
                config.setRateLimiterPassed(stats.getRateLimiterPassed());
                config.setRateLimiterRejected(stats.getRateLimiterRejected());
                config.setCircuitBreakerState(stats.getCircuitBreakerState());
            });
        }
        return configs;
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

        // 如果找到配置，附加统计数据（从持久化存储读取）
        if (config != null) {
            statsRepository.findByServiceName(serviceName).ifPresent(stats -> {
                config.setRateLimiterPassed(stats.getRateLimiterPassed());
                config.setRateLimiterRejected(stats.getRateLimiterRejected());
                config.setCircuitBreakerState(stats.getCircuitBreakerState());
            });
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
        statsRepository.deleteByServiceName(serviceName);
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

    /**
     * 更新统计数据（Consumer 上报）- 持久化存储
     */
    @Transactional
    public void updateStats(String serviceName, Long passed, Long rejected, String circuitBreakerState) {
        ProtectionStatsEntity stats = getOrCreateStats(serviceName);

        stats.setRateLimiterPassed(passed);
        stats.setRateLimiterRejected(rejected);
        stats.setCircuitBreakerState(circuitBreakerState);

        // 如果熔断器状态变为 OPEN，记录熔断时间
        if ("OPEN".equals(circuitBreakerState) && !"OPEN".equals(stats.getCircuitBreakerState())) {
            stats.setLastTripTime(LocalDateTime.now());
            stats.setCircuitBreakerOpenCount(stats.getCircuitBreakerOpenCount() + 1);
        }

        statsRepository.save(stats);

        logger.debug("Updated stats for {}: passed={}, rejected={}, cbState={}",
                serviceName, passed, rejected, circuitBreakerState);
    }

    /**
     * 获取总通过数
     */
    public long getTotalPassed() {
        return statsRepository.findAll().stream()
                .mapToLong(ProtectionStatsEntity::getRateLimiterPassed)
                .sum();
    }

    /**
     * 获取总拒绝数
     */
    public long getTotalRejected() {
        return statsRepository.findAll().stream()
                .mapToLong(ProtectionStatsEntity::getRateLimiterRejected)
                .sum();
    }

    /**
     * 获取服务通过数
     */
    public long getPassed(String serviceName) {
        return statsRepository.findByServiceName(serviceName)
                .map(ProtectionStatsEntity::getRateLimiterPassed)
                .orElse(0L);
    }

    /**
     * 获取服务拒绝数
     */
    public long getRejected(String serviceName) {
        return statsRepository.findByServiceName(serviceName)
                .map(ProtectionStatsEntity::getRateLimiterRejected)
                .orElse(0L);
    }
}