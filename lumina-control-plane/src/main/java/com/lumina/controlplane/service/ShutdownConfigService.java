package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ShutdownConfigEntity;
import com.lumina.controlplane.repository.ShutdownConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 优雅停机配置服务
 */
@Service
public class ShutdownConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ShutdownConfigService.class);

    private final ShutdownConfigRepository repository;

    public ShutdownConfigService(ShutdownConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取所有配置
     */
    public List<ShutdownConfigEntity> findAll() {
        return repository.findAll();
    }

    /**
     * 获取指定服务的配置
     */
    public ShutdownConfigEntity findByServiceName(String serviceName) {
        return repository.findByServiceName(serviceName).orElse(null);
    }

    /**
     * 获取或创建配置
     */
    @Transactional
    public ShutdownConfigEntity getOrCreate(String serviceName) {
        Optional<ShutdownConfigEntity> existing = repository.findByServiceName(serviceName);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 创建默认配置
        ShutdownConfigEntity config = new ShutdownConfigEntity();
        config.setServiceName(serviceName);
        config.setEnabled(true);
        config.setTimeoutMs(10000L);
        config.setShuttingDown(false);
        config.setActiveRequests(0);

        return repository.save(config);
    }

    /**
     * 更新停机配置
     */
    @Transactional
    public ShutdownConfigEntity updateConfig(String serviceName, Boolean enabled, Long timeoutMs) {
        ShutdownConfigEntity config = getOrCreate(serviceName);

        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (timeoutMs != null) {
            config.setTimeoutMs(timeoutMs);
        }

        return repository.save(config);
    }

    /**
     * 触发停机
     */
    @Transactional
    public ShutdownConfigEntity triggerShutdown(String serviceName) {
        ShutdownConfigEntity config = getOrCreate(serviceName);
        config.setShuttingDown(true);
        logger.info("🛑 Shutdown triggered for service: {}", serviceName);
        return repository.save(config);
    }

    /**
     * 取消停机
     */
    @Transactional
    public ShutdownConfigEntity cancelShutdown(String serviceName) {
        ShutdownConfigEntity config = repository.findByServiceName(serviceName).orElse(null);
        if (config != null) {
            config.setShuttingDown(false);
            logger.info("✅ Shutdown cancelled for service: {}", serviceName);
            return repository.save(config);
        }
        return null;
    }

    /**
     * 更新活跃请求数（Provider 上报）
     */
    @Transactional
    public void updateActiveRequests(String serviceName, int activeRequests) {
        ShutdownConfigEntity config = getOrCreate(serviceName);
        config.setActiveRequests(activeRequests);
        repository.save(config);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void delete(String serviceName) {
        repository.deleteByServiceName(serviceName);
    }

    /**
     * 获取停机状态概览
     */
    public ShutdownStatusOverview getStatusOverview() {
        List<ShutdownConfigEntity> configs = repository.findAll();

        int totalServices = configs.size();
        int shuttingDown = 0;
        int running = 0;
        int totalActiveRequests = 0;

        for (ShutdownConfigEntity config : configs) {
            if (Boolean.TRUE.equals(config.getShuttingDown())) {
                shuttingDown++;
            } else {
                running++;
            }
            if (config.getActiveRequests() != null) {
                totalActiveRequests += config.getActiveRequests();
            }
        }

        return new ShutdownStatusOverview(totalServices, running, shuttingDown, totalActiveRequests);
    }

    public static class ShutdownStatusOverview {
        public final int totalServices;
        public final int running;
        public final int shuttingDown;
        public final int totalActiveRequests;

        public ShutdownStatusOverview(int totalServices, int running, int shuttingDown, int totalActiveRequests) {
            this.totalServices = totalServices;
            this.running = running;
            this.shuttingDown = shuttingDown;
            this.totalActiveRequests = totalActiveRequests;
        }
    }
}