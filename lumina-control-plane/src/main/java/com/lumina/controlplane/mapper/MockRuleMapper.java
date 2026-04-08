package com.lumina.controlplane.mapper;

import com.lumina.controlplane.entity.MockRuleEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mock 规则 Mapper
 */
public interface MockRuleMapper extends BaseMapper<MockRuleEntity> {

    @Select("SELECT * FROM lumina_mock_rule WHERE service_name = #{serviceName} AND enabled = true")
    List<MockRuleEntity> findByServiceNameAndEnabledTrue(@Param("serviceName") String serviceName);

    @Select("SELECT * FROM lumina_mock_rule WHERE service_name = #{serviceName} AND method_name = #{methodName} AND enabled = true")
    List<MockRuleEntity> findByServiceNameAndMethodNameAndEnabledTrue(
            @Param("serviceName") String serviceName,
            @Param("methodName") String methodName);

    @Select("SELECT * FROM lumina_mock_rule WHERE enabled = true")
    List<MockRuleEntity> findByEnabledTrue();

    @Select("SELECT * FROM lumina_mock_rule WHERE service_name = #{serviceName} AND enabled = true ORDER BY priority DESC")
    List<MockRuleEntity> findActiveRulesByServiceOrderByPriority(@Param("serviceName") String serviceName);

    @Select("SELECT DISTINCT service_name FROM lumina_mock_rule WHERE enabled = true")
    List<String> findDistinctServiceNames();

    @Select("SELECT COUNT(*) FROM lumina_mock_rule WHERE enabled = true")
    long countByEnabledTrue();
}