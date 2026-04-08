package com.lumina.controlplane.mapper;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 保护配置 Mapper
 */
public interface ProtectionConfigMapper extends BaseMapper<ProtectionConfigEntity> {

    @Select("SELECT * FROM lumina_protection_config WHERE service_name = #{serviceName}")
    ProtectionConfigEntity findByServiceName(@Param("serviceName") String serviceName);

    void deleteByServiceName(@Param("serviceName") String serviceName);
}