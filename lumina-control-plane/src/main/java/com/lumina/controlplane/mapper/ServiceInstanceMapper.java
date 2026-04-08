package com.lumina.controlplane.mapper;

import com.lumina.controlplane.entity.ServiceInstanceEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务实例 Mapper
 */
public interface ServiceInstanceMapper extends BaseMapper<ServiceInstanceEntity> {

    @Select("SELECT * FROM lumina_service_instance WHERE service_name = #{serviceName}")
    List<ServiceInstanceEntity> findByServiceName(@Param("serviceName") String serviceName);

    @Select("SELECT * FROM lumina_service_instance WHERE instance_id = #{instanceId}")
    ServiceInstanceEntity findByInstanceId(@Param("instanceId") String instanceId);

    @Select("SELECT * FROM lumina_service_instance WHERE status = #{status}")
    List<ServiceInstanceEntity> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM lumina_service_instance WHERE status = 'UP' AND (expires_at < #{now} OR expires_at IS NULL)")
    List<ServiceInstanceEntity> findExpiredInstances(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM lumina_service_instance WHERE status = 'UP' AND expires_at > #{now}")
    List<ServiceInstanceEntity> findHealthyInstances(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(DISTINCT service_name) FROM lumina_service_instance WHERE status = 'UP' AND expires_at > #{now}")
    long countDistinctHealthyServices(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM lumina_service_instance WHERE status = 'UP' AND expires_at > #{now}")
    long countHealthyInstances(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM lumina_service_instance WHERE status = 'DOWN' OR expires_at > #{now}")
    List<ServiceInstanceEntity> findAllNonExpired(@Param("now") LocalDateTime now);

    void deleteByServiceName(@Param("serviceName") String serviceName);
}