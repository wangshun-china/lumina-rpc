package com.lumina.controlplane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.controlplane.entity.ServiceInstanceEntity;
import com.lumina.controlplane.mapper.ServiceInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ServiceInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceService.class);

    private final ServiceInstanceMapper mapper;
    private final ObjectMapper objectMapper;

    public ServiceInstanceService(ServiceInstanceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<ServiceInstanceEntity> findAll() {
        return mapper.findAllNonExpired(LocalDateTime.now());
    }

    public List<ServiceInstanceEntity> findAllIncludingExpired() {
        return mapper.selectAll();
    }

    public List<ServiceInstanceEntity> findByServiceName(String serviceName) {
        return mapper.findByServiceName(serviceName);
    }

    public ServiceInstanceEntity findByInstanceId(String instanceId) {
        return mapper.findByInstanceId(instanceId);
    }

    public List<ServiceInstanceEntity> findHealthyInstances() {
        return mapper.findHealthyInstances(LocalDateTime.now());
    }

    public long countDistinctHealthyServices() {
        return mapper.countDistinctHealthyServices(LocalDateTime.now());
    }

    public long countHealthyInstances() {
        return mapper.countHealthyInstances(LocalDateTime.now());
    }

    public Map<String, Map<String, Object>> getAllServiceMetadata() {
        List<ServiceInstanceEntity> instances = findHealthyInstances();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        for (ServiceInstanceEntity instance : instances) {
            String serviceName = instance.getServiceName();
            if (!result.containsKey(serviceName) && instance.getServiceMetadata() != null) {
                try {
                    Map<String, Object> metadata = objectMapper.readValue(
                        instance.getServiceMetadata(),
                        new TypeReference<Map<String, Object>>() {}
                    );
                    result.put(serviceName, metadata);
                    logger.debug("Loaded metadata for service: {}", serviceName);
                } catch (Exception e) {
                    logger.error("Failed to parse metadata for service: {}", serviceName, e);
                }
            }
        }

        return result;
    }

    public Map<String, Object> getServiceMetadata(String serviceName) {
        List<ServiceInstanceEntity> instances = mapper.findByServiceName(serviceName);
        ServiceInstanceEntity instance = instances.stream()
                .filter(i -> "UP".equals(i.getStatus()))
                .findFirst()
                .orElse(null);

        if (instance != null && instance.getServiceMetadata() != null) {
            try {
                return objectMapper.readValue(
                    instance.getServiceMetadata(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                logger.error("Failed to parse metadata for service: {}", serviceName, e);
            }
        }
        return new HashMap<>();
    }

    @Transactional
    public ServiceInstanceEntity register(ServiceInstanceEntity instance) {
        String instanceId = instance.getInstanceId();

        if (instanceId == null || instanceId.isEmpty()) {
            instanceId = instance.getServiceName() + "@" + instance.getHost() + ":" + instance.getPort();
            instance.setInstanceId(instanceId);
        }

        logger.info("Registering service instance: {} - {}", instance.getServiceName(), instanceId);

        ServiceInstanceEntity existing = mapper.findByInstanceId(instanceId);

        if (existing != null) {
            existing.setStatus("UP");
            existing.setHost(instance.getHost());
            existing.setPort(instance.getPort());
            existing.setVersion(instance.getVersion());
            existing.setMetadata(instance.getMetadata());
            existing.setServiceMetadata(instance.getServiceMetadata());
            existing.setLastHeartbeat(LocalDateTime.now());
            existing.setExpiresAt(LocalDateTime.now().plusSeconds(90));

            if (existing.getStartTime() == null && instance.getStartTime() != null) {
                existing.setStartTime(instance.getStartTime());
            }

            if (instance.getWarmupPeriod() != null) {
                existing.setWarmupPeriod(instance.getWarmupPeriod());
            }

            logger.info("✅ Updated existing service instance: {}", instanceId);
            mapper.update(existing);
            return existing;
        } else {
            instance.setStatus("UP");
            instance.setRegisteredAt(LocalDateTime.now());
            instance.setLastHeartbeat(LocalDateTime.now());
            instance.setExpiresAt(LocalDateTime.now().plusSeconds(90));

            if (instance.getStartTime() == null) {
                instance.setStartTime(System.currentTimeMillis());
            }

            logger.info("✅ Created new service instance: {}", instanceId);
            mapper.insert(instance);
            return instance;
        }
    }

    @Transactional
    public void heartbeat(String instanceId) {
        ServiceInstanceEntity instance = mapper.findByInstanceId(instanceId);
        if (instance != null) {
            instance.setLastHeartbeat(LocalDateTime.now());
            instance.setStatus("UP");
            instance.setExpiresAt(LocalDateTime.now().plusSeconds(90));
            mapper.update(instance);
            logger.debug("Heartbeat received for instance: {}", instanceId);
        } else {
            logger.warn("Heartbeat received for unknown instance: {}", instanceId);
        }
    }

    @Transactional
    public void deregister(String instanceId) {
        ServiceInstanceEntity instance = mapper.findByInstanceId(instanceId);
        if (instance != null) {
            instance.setStatus("DOWN");
            mapper.update(instance);
            logger.info("Deregistered service instance: {}", instanceId);
        }
    }

    @Transactional
    public void cleanupExpiredInstances() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 将心跳超时的实例标记为 DOWN
        List<ServiceInstanceEntity> expired = mapper.findExpiredInstances(now);
        for (ServiceInstanceEntity instance : expired) {
            instance.setStatus("DOWN");
            mapper.update(instance);
            logger.info("Marked expired instance as DOWN: {}", instance.getInstanceId());
        }

        // 2. 物理删除：DOWN 状态超过 1 小时的僵尸实例
        LocalDateTime oneHourAgo = now.minusHours(1);
        List<ServiceInstanceEntity> zombieInstances = mapper.findByStatus("DOWN");
        for (ServiceInstanceEntity instance : zombieInstances) {
            if (instance.getLastHeartbeat() != null && instance.getLastHeartbeat().isBefore(oneHourAgo)) {
                mapper.deleteById(instance.getId());
                logger.info("🗑️ Deleted zombie instance (DOWN > 1h): {}", instance.getInstanceId());
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledCleanup() {
        cleanupExpiredInstances();
    }
}