package com.lumina.rpc.core.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 服务注册表
 *
 * 用于本地缓存和管理服务提供者实例
 */
public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    // 服务实例缓存: interfaceName + "#" + version -> serviceBean
    private final ConcurrentHashMap<String, Object> serviceCache;

    public ServiceRegistry() {
        this.serviceCache = new ConcurrentHashMap<>();
    }

    /**
     * 生成服务缓存的 key
     *
     * @param interfaceName 接口名
     * @param version       版本号
     * @return key
     */
    private String buildKey(String interfaceName, String version) {
        return interfaceName + "#" + (version != null ? version : "");
    }

    /**
     * 注册服务
     *
     * @param interfaceName 接口名
     * @param version       版本号
     * @param serviceBean   服务实例
     */
    public void registerService(String interfaceName, String version, Object serviceBean) {
        String key = buildKey(interfaceName, version);
        serviceCache.put(key, serviceBean);
        logger.info("Registered service: interface={}, version={}", interfaceName, version);
    }

    /**
     * 获取服务实例
     *
     * @param interfaceName 接口名
     * @param version       版本号
     * @return 服务实例，如果不存在返回 null
     */
    public Object getService(String interfaceName, String version) {
        String key = buildKey(interfaceName, version);
        Object service = serviceCache.get(key);
        if (service == null) {
            // 尝试获取不带版本的服务
            key = buildKey(interfaceName, "");
            service = serviceCache.get(key);
        }
        return service;
    }

    /**
     * 注销服务
     *
     * @param interfaceName 接口名
     * @param version       版本号
     * @return 被移除的服务实例，如果不存在返回 null
     */
    public Object unregisterService(String interfaceName, String version) {
        String key = buildKey(interfaceName, version);
        Object service = serviceCache.remove(key);
        if (service != null) {
            logger.info("Unregistered service: interface={}, version={}", interfaceName, version);
        }
        return service;
    }

    /**
     * 检查服务是否已注册
     *
     * @param interfaceName 接口名
     * @param version       版本号
     * @return true 如果服务已注册
     */
    public boolean isServiceRegistered(String interfaceName, String version) {
        String key = buildKey(interfaceName, version);
        return serviceCache.containsKey(key);
    }

    /**
     * 获取已注册服务数量
     *
     * @return 服务数量
     */
    public int getServiceCount() {
        return serviceCache.size();
    }

    /**
     * 获取所有已注册的服务
     * key -> interfaceName#version
     *
     * @return 服务缓存的副本
     */
    public Map<String, Object> getAllServices() {
        return Collections.unmodifiableMap(serviceCache);
    }

    /**
     * 获取所有已注册的服务名称（去重）
     *
     * @return 服务名称集合
     */
    public Set<String> getAllServiceNames() {
        return serviceCache.keySet().stream()
                .map(key -> key.split("#")[0])
                .collect(Collectors.toSet());
    }

    /**
     * 清空所有服务
     */
    public void clear() {
        serviceCache.clear();
        logger.info("Cleared all registered services");
    }
}
