package com.lumina.rpc.core.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 服务发现本地缓存
 *
 * 从控制平面拉取服务列表后缓存在本地
 * 供 RpcClientHandler 在发起请求时查询可用服务实例
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    // 服务实例缓存: serviceName -> List<ServiceInstance>
    private static final Map<String, List<ServiceInstance>> SERVICE_REGISTRY = new ConcurrentHashMap<>();

    // 控制平面地址
    private static String controlPlaneUrl = "http://localhost:8080";

    /**
     * 更新服务实例列表
     *
     * @param serviceName    服务名称
     * @param instances 新的服务实例列表
     */
    public static void updateServiceInstances(String serviceName, List<ServiceInstance> instances) {
        if (serviceName == null || serviceName.isEmpty()) {
            logger.warn("Cannot update service with empty name");
            return;
        }

        SERVICE_REGISTRY.put(serviceName, new CopyOnWriteArrayList<>(instances));
        logger.info("Updated service instances: {} -> {} instances", serviceName, instances.size());
    }

    /**
     * 获取服务实例列表
     *
     * @param serviceName 服务名称
     * @return 服务实例列表
     */
    public static List<ServiceInstance> getServiceInstances(String serviceName) {
        List<ServiceInstance> instances = SERVICE_REGISTRY.get(serviceName);
        return instances != null ? new ArrayList<>(instances) : new ArrayList<>();
    }

    /**
     * 根据版本获取服务实例列表
     *
     * @param serviceName 服务名称
     * @param version     版本号
     * @return 符合版本的服务实例列表
     */
    public static List<ServiceInstance> getServiceInstances(String serviceName, String version) {
        List<ServiceInstance> allInstances = getServiceInstances(serviceName);
        if (version == null || version.isEmpty()) {
            return allInstances;
        }

        List<ServiceInstance> filtered = new ArrayList<>();
        for (ServiceInstance instance : allInstances) {
            if (version.equals(instance.getVersion())) {
                filtered.add(instance);
            }
        }
        return filtered;
    }

    /**
     * 移除服务实例列表
     *
     * @param serviceName 服务名称
     */
    public static void removeService(String serviceName) {
        SERVICE_REGISTRY.remove(serviceName);
        logger.info("Removed service: {}", serviceName);
    }

    /**
     * 清除所有服务实例
     */
    public static void clear() {
        SERVICE_REGISTRY.clear();
        logger.info("Cleared all service instances");
    }

    /**
     * 获取所有已注册的服务名称
     *
     * @return 服务名称列表
     */
    public static List<String> getAllServiceNames() {
        return new ArrayList<>(SERVICE_REGISTRY.keySet());
    }

    /**
     * 检查是否有可用的服务实例
     *
     * @param serviceName 服务名称
     * @return 是否有可用实例
     */
    public static boolean hasAvailableService(String serviceName) {
        List<ServiceInstance> instances = SERVICE_REGISTRY.get(serviceName);
        return instances != null && !instances.isEmpty();
    }

    /**
     * 设置控制平面地址
     *
     * @param url 控制平面 URL
     */
    public static void setControlPlaneUrl(String url) {
        controlPlaneUrl = url;
    }

    /**
     * 获取控制平面地址
     *
     * @return 控制平面 URL
     */
    public static String getControlPlaneUrl() {
        return controlPlaneUrl;
    }
}