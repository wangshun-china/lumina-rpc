package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.ServiceInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, Long> {

    List<ServiceInstanceEntity> findByServiceName(String serviceName);

    Optional<ServiceInstanceEntity> findByInstanceId(String instanceId);

    List<ServiceInstanceEntity> findByStatus(String status);

    @Query("SELECT s FROM ServiceInstanceEntity s WHERE s.status = 'UP' AND (s.expiresAt < :now OR s.expiresAt IS NULL)")
    List<ServiceInstanceEntity> findExpiredInstances(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM ServiceInstanceEntity s WHERE s.status = 'UP' AND s.expiresAt > :now")
    List<ServiceInstanceEntity> findHealthyInstances(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(DISTINCT s.serviceName) FROM ServiceInstanceEntity s WHERE s.status = 'UP' AND s.expiresAt > :now")
    long countDistinctHealthyServices(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM ServiceInstanceEntity s WHERE s.status = 'UP' AND s.expiresAt > :now")
    long countHealthyInstances(@Param("now") LocalDateTime now);

    /**
     * 查询所有非过期实例（用于服务列表展示）
     * 包括健康的 UP 实例和已主动下线的 DOWN 实例
     * 排除心跳超时但状态仍为 UP 的僵尸实例
     */
    @Query("SELECT s FROM ServiceInstanceEntity s WHERE s.status = 'DOWN' OR s.expiresAt > :now")
    List<ServiceInstanceEntity> findAllNonExpired(@Param("now") LocalDateTime now);
}
