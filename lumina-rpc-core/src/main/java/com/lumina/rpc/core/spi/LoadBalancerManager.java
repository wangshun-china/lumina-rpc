package com.lumina.rpc.core.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡器管理器
 */
public class LoadBalancerManager {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerManager.class);

    // 存储所有已注册的负载均衡器
    private static final ConcurrentHashMap<String, LoadBalancer> LOAD_BALANCERS = new ConcurrentHashMap<>();

    // 默认负载均衡器名称
    private static volatile String defaultLoadBalancerName = "weighted-round-robin";

    static {
        // 加载 SPI 实现的负载均衡器
        loadLoadBalancers();
        // 注册内置负载均衡器
        registerLoadBalancer(new RoundRobinLoadBalancer());
        registerLoadBalancer(new RandomLoadBalancer());
        registerLoadBalancer(new WeightedRoundRobinLoadBalancer());
        registerLoadBalancer(new LeastActiveLoadBalancer());
        registerLoadBalancer(new ConsistentHashLoadBalancer());
    }

    /**
     * 通过 SPI 加载负载均衡器
     */
    private static void loadLoadBalancers() {
        ServiceLoader<LoadBalancer> loader = ServiceLoader.load(LoadBalancer.class);
        for (LoadBalancer loadBalancer : loader) {
            registerLoadBalancer(loadBalancer);
            logger.info("Loaded load balancer via SPI: {}", loadBalancer.getName());
        }
    }

    /**
     * 注册负载均衡器
     *
     * @param loadBalancer 负载均衡器
     */
    public static void registerLoadBalancer(LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            return;
        }
        LOAD_BALANCERS.put(loadBalancer.getName(), loadBalancer);
        logger.debug("Registered load balancer: {}", loadBalancer.getName());
    }

    /**
     * 获取负载均衡器
     *
     * @param name 负载均衡器名称
     * @return 负载均衡器
     */
    public static LoadBalancer getLoadBalancer(String name) {
        LoadBalancer loadBalancer = LOAD_BALANCERS.get(name);
        if (loadBalancer == null) {
            logger.warn("Load balancer not found: {}, using default weighted-round-robin", name);
            return LOAD_BALANCERS.get("weighted-round-robin");
        }
        return loadBalancer;
    }

    /**
     * 获取默认负载均衡器
     *
     * @return 默认负载均衡器
     */
    public static LoadBalancer getDefaultLoadBalancer() {
        return getLoadBalancer(defaultLoadBalancerName);
    }

    /**
     * 设置默认负载均衡器名称
     *
     * @param name 负载均衡器名称
     */
    public static void setDefaultLoadBalancerName(String name) {
        if (LOAD_BALANCERS.containsKey(name)) {
            defaultLoadBalancerName = name;
            logger.info("Default load balancer set to: {}", name);
        } else {
            logger.warn("Cannot set default load balancer: {} not registered", name);
        }
    }
}
