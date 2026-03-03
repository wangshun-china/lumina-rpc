package com.lumina.controlplane.service;

import com.lumina.controlplane.entity.ServiceInstanceEntity;
import com.lumina.controlplane.repository.ServiceInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ServiceInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceService.class);

    private final ServiceInstanceRepository serviceInstanceRepository;

    public ServiceInstanceService(ServiceInstanceRepository serviceInstanceRepository) {
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    public List<ServiceInstanceEntity> findAll() {
        // 只返回非过期实例，排除心跳超时的僵尸实例
        return serviceInstanceRepository.findAllNonExpired(LocalDateTime.now());
    }

    /**
     * 查询所有实例（包括过期的，用于调试/管理）
     */
    public List<ServiceInstanceEntity> findAllIncludingExpired() {
        return serviceInstanceRepository.findAll();
    }

    public List<ServiceInstanceEntity> findByServiceName(String serviceName) {
        return serviceInstanceRepository.findByServiceName(serviceName);
    }

    public Optional<ServiceInstanceEntity> findByInstanceId(String instanceId) {
        return serviceInstanceRepository.findByInstanceId(instanceId);
    }

    public List<ServiceInstanceEntity> findHealthyInstances() {
        return serviceInstanceRepository.findHealthyInstances(LocalDateTime.now());
    }

    public long countDistinctHealthyServices() {
        return serviceInstanceRepository.countDistinctHealthyServices(LocalDateTime.now());
    }

    public long countHealthyInstances() {
        return serviceInstanceRepository.countHealthyInstances(LocalDateTime.now());
    }

    @Transactional
    public ServiceInstanceEntity register(ServiceInstanceEntity instance) {
        String instanceId = instance.getInstanceId();

        // 如果没有传 instanceId，根据 serviceName + host + port 生成
        if (instanceId == null || instanceId.isEmpty()) {
            instanceId = instance.getServiceName() + "@" + instance.getHost() + ":" + instance.getPort();
            instance.setInstanceId(instanceId);
        }

        logger.info("Registering service instance: {} - {}", instance.getServiceName(), instanceId);

        // 幂等更新：先根据 instanceId 查询
        Optional<ServiceInstanceEntity> existing = serviceInstanceRepository.findByInstanceId(instanceId);

        if (existing.isPresent()) {
            // 已存在：更新该记录的状态和元数据
            ServiceInstanceEntity entity = existing.get();
            entity.setStatus("UP");
            entity.setHost(instance.getHost());
            entity.setPort(instance.getPort());
            entity.setVersion(instance.getVersion());
            entity.setMetadata(instance.getMetadata());
            entity.setServiceMetadata(instance.getServiceMetadata());
            entity.setLastHeartbeat(LocalDateTime.now());
            entity.setExpiresAt(LocalDateTime.now().plusSeconds(90)); // 90秒过期
            logger.info("✅ Updated existing service instance: {} (idempotent update)", instanceId);
            return serviceInstanceRepository.save(entity);
        } else {
            // 不存在：创建新记录
            instance.setStatus("UP");
            instance.setRegisteredAt(LocalDateTime.now());
            instance.setLastHeartbeat(LocalDateTime.now());
            instance.setExpiresAt(LocalDateTime.now().plusSeconds(90)); // 90秒过期
            logger.info("✅ Created new service instance: {}", instanceId);
            return serviceInstanceRepository.save(instance);
        }
    }

    @Transactional
    public void heartbeat(String instanceId) {
        Optional<ServiceInstanceEntity> optional = serviceInstanceRepository.findByInstanceId(instanceId);
        if (optional.isPresent()) {
            ServiceInstanceEntity instance = optional.get();
            instance.setLastHeartbeat(LocalDateTime.now());
            instance.setStatus("UP");
            instance.setExpiresAt(LocalDateTime.now().plusSeconds(90)); // 90秒过期
            serviceInstanceRepository.save(instance);
            logger.debug("Heartbeat received for instance: {}", instanceId);
        } else {
            logger.warn("Heartbeat received for unknown instance: {}", instanceId);
        }
    }

    @Transactional
    public void deregister(String instanceId) {
        Optional<ServiceInstanceEntity> optional = serviceInstanceRepository.findByInstanceId(instanceId);
        if (optional.isPresent()) {
            ServiceInstanceEntity instance = optional.get();
            instance.setStatus("DOWN");
            serviceInstanceRepository.save(instance);
            logger.info("Deregistered service instance: {}", instanceId);
        }
    }

    @Transactional
    public void cleanupExpiredInstances() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 将心跳超时的实例标记为 DOWN
        List<ServiceInstanceEntity> expired = serviceInstanceRepository.findExpiredInstances(now);
        for (ServiceInstanceEntity instance : expired) {
            instance.setStatus("DOWN");
            serviceInstanceRepository.save(instance);
            logger.info("Marked expired instance as DOWN: {}", instance.getInstanceId());
        }

        // 2. 物理删除：DOWN 状态超过 1 小时的僵尸实例
        LocalDateTime oneHourAgo = now.minusHours(1);
        List<ServiceInstanceEntity> zombieInstances = serviceInstanceRepository.findByStatus("DOWN");
        for (ServiceInstanceEntity instance : zombieInstances) {
            if (instance.getLastHeartbeat() != null && instance.getLastHeartbeat().isBefore(oneHourAgo)) {
                serviceInstanceRepository.delete(instance);
                logger.info("🗑️ Deleted zombie instance (DOWN > 1h): {}", instance.getInstanceId());
            }
        }
    }

    /**
     * 定时清理任务：每 60 秒执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledCleanup() {
        cleanupExpiredInstances();
    }
}
