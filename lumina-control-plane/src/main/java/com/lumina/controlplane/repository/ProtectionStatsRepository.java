package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.ProtectionStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 保护统计数据 Repository
 */
public interface ProtectionStatsRepository extends JpaRepository<ProtectionStatsEntity, Long> {

    /**
     * 根据服务名查找统计数据
     */
    Optional<ProtectionStatsEntity> findByServiceName(String serviceName);

    /**
     * 根据服务名删除统计数据
     */
    @Transactional
    void deleteByServiceName(String serviceName);
}