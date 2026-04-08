package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.lumina.controlplane.mapper.ProtectionConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 保护配置服务
 */
@Service
public class ProtectionConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionConfigService.class);

    private final ProtectionConfigMapper mapper;
    private final SseBroadcastService sseBroadcastService;

    public ProtectionConfigService(ProtectionConfigMapper mapper,
                                   SseBroadcastService sseBroadcastService) {
        this.mapper = mapper;
        this.sseBroadcastService = sseBroadcastService;
        logger.info("ProtectionConfigService initialized");
    }

    public List<ProtectionConfigEntity> findAll() {
        return mapper.selectAll();
    }

    public ProtectionConfigEntity findByServiceName(String serviceName) {
        return mapper.findByServiceName(serviceName);
    }

    public ProtectionConfigEntity getOrCreateDefault(String serviceName) {
        ProtectionConfigEntity config = mapper.findByServiceName(serviceName);
        if (config == null) {
            config = new ProtectionConfigEntity();
            config.setServiceName(serviceName);
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            mapper.insert(config);
            logger.info("Created default protection config for service: {}", serviceName);
        }
        return config;
    }

    @Transactional
    public ProtectionConfigEntity save(ProtectionConfigEntity config) {
        if (config.getId() == null) {
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            mapper.insert(config);
        } else {
            config.setUpdatedAt(LocalDateTime.now());
            mapper.update(config);
        }
        logger.info("Saved protection config for service: {}", config.getServiceName());

        broadcastConfigChange(config);

        return config;
    }

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

    @Transactional
    public void delete(String serviceName) {
        mapper.deleteByServiceName(serviceName);
        logger.info("Deleted protection config for service: {}", serviceName);
    }

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