package com.lumina.controlplane.controller;

import com.lumina.controlplane.entity.ServiceInstanceEntity;
import com.lumina.controlplane.service.ServiceInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 服务实例管理控制器
 * 提供服务实例的注册、发现、健康检查等接口
 */
@RestController
@RequestMapping("/api/v1/registry")
public class ServiceInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceController.class);

    private final ServiceInstanceService serviceInstanceService;

    public ServiceInstanceController(ServiceInstanceService serviceInstanceService) {
        this.serviceInstanceService = serviceInstanceService;
    }

    /**
     * 获取所有服务实例
     */
    @GetMapping("/instances")
    public ResponseEntity<List<ServiceInstanceEntity>> getAllInstances() {
        logger.debug("Getting all service instances");
        return ResponseEntity.ok(serviceInstanceService.findAll());
    }

    /**
     * 根据服务名获取实例列表
     */
    @GetMapping("/instances/{serviceName}")
    public ResponseEntity<List<ServiceInstanceEntity>> getInstancesByService(
            @PathVariable("serviceName") String serviceName) {
        logger.debug("Getting instances for service: {}", serviceName);
        return ResponseEntity.ok(serviceInstanceService.findByServiceName(serviceName));
    }

    /**
     * 根据实例ID获取实例详情
     */
    @GetMapping("/instance/{instanceId}")
    public ResponseEntity<ServiceInstanceEntity> getInstanceById(
            @PathVariable("instanceId") String instanceId) {
        logger.debug("Getting instance by id: {}", instanceId);
        return serviceInstanceService.findByInstanceId(instanceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取健康的服务实例
     */
    @GetMapping("/instances/healthy")
    public ResponseEntity<List<ServiceInstanceEntity>> getHealthyInstances() {
        logger.debug("Getting healthy service instances");
        return ResponseEntity.ok(serviceInstanceService.findHealthyInstances());
    }

    /**
     * 注册服务实例
     */
    @PostMapping("/register")
    public ResponseEntity<ServiceInstanceEntity> register(@RequestBody ServiceInstanceEntity instance) {
        logger.info("Registering service instance: {} - {}", instance.getServiceName(), instance.getInstanceId());
        ServiceInstanceEntity registered = serviceInstanceService.register(instance);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * 服务实例心跳
     */
    @PostMapping("/heartbeat/{instanceId}")
    public ResponseEntity<Void> heartbeat(@PathVariable("instanceId") String instanceId) {
        logger.debug("Received heartbeat for instance: {}", instanceId);
        serviceInstanceService.heartbeat(instanceId);
        return ResponseEntity.ok().build();
    }

    /**
     * 注销服务实例
     */
    @PostMapping("/deregister/{instanceId}")
    public ResponseEntity<Void> deregister(@PathVariable("instanceId") String instanceId) {
        logger.info("Deregistering service instance: {}", instanceId);
        serviceInstanceService.deregister(instanceId);
        return ResponseEntity.ok().build();
    }

    /**
     * 清理过期实例（内部管理接口）
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Void> cleanupExpiredInstances() {
        logger.info("Cleaning up expired service instances");
        serviceInstanceService.cleanupExpiredInstances();
        return ResponseEntity.ok().build();
    }
}
