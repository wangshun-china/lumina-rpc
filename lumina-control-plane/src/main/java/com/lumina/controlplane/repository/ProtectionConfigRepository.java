package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.ProtectionConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProtectionConfigRepository extends JpaRepository<ProtectionConfigEntity, Long> {

    Optional<ProtectionConfigEntity> findByServiceName(String serviceName);

    void deleteByServiceName(String serviceName);
}