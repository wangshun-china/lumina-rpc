package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.MockRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockRuleRepository extends JpaRepository<MockRuleEntity, Long> {

    List<MockRuleEntity> findByServiceNameAndEnabledTrue(String serviceName);

    List<MockRuleEntity> findByServiceNameAndMethodNameAndEnabledTrue(String serviceName, String methodName);

    List<MockRuleEntity> findByEnabledTrue();

    @Query("SELECT r FROM MockRuleEntity r WHERE r.serviceName = :serviceName AND r.enabled = true ORDER BY r.priority DESC")
    List<MockRuleEntity> findActiveRulesByServiceOrderByPriority(@Param("serviceName") String serviceName);

    @Query("SELECT DISTINCT r.serviceName FROM MockRuleEntity r WHERE r.enabled = true")
    List<String> findDistinctServiceNames();

    /**
     * 统计启用的规则数量
     */
    long countByEnabledTrue();
}
