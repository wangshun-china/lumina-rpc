package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.ShutdownConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShutdownConfigRepository extends JpaRepository<ShutdownConfigEntity, Long> {

    Optional<ShutdownConfigEntity> findByServiceName(String serviceName);

    void deleteByServiceName(String serviceName);
}