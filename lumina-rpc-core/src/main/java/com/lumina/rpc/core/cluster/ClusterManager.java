package com.lumina.rpc.core.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群容错策略管理器
 *
 * 管理所有可用的集群策略
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class ClusterManager {

    private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);

    /** 单例实例 */
    private static volatile ClusterManager instance;

    /** 策略注册表 */
    private final Map<String, Cluster> clusters = new ConcurrentHashMap<>();

    /** 默认策略 */
    private volatile String defaultCluster = FailoverCluster.NAME;

    private ClusterManager() {
        // 注册内置策略
        registerCluster(new FailoverCluster());
        registerCluster(new FailfastCluster());
        registerCluster(new FailsafeCluster());
        registerCluster(new ForkingCluster());

        logger.info("ClusterManager initialized with {} strategies: {}",
                clusters.size(), clusters.keySet());
    }

    /**
     * 获取单例实例
     */
    public static ClusterManager getInstance() {
        if (instance == null) {
            synchronized (ClusterManager.class) {
                if (instance == null) {
                    instance = new ClusterManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册集群策略
     */
    public void registerCluster(Cluster cluster) {
        clusters.put(cluster.getName(), cluster);
        logger.debug("Registered cluster strategy: {}", cluster.getName());
    }

    /**
     * 获取集群策略
     *
     * @param name 策略名称
     * @return 集群策略
     */
    public Cluster getCluster(String name) {
        if (name == null || name.isEmpty()) {
            name = defaultCluster;
        }

        Cluster cluster = clusters.get(name);
        if (cluster == null) {
            logger.warn("Unknown cluster strategy: {}, using default: {}", name, defaultCluster);
            return clusters.get(defaultCluster);
        }
        return cluster;
    }

    /**
     * 获取默认集群策略
     */
    public Cluster getDefaultCluster() {
        return clusters.get(defaultCluster);
    }

    /**
     * 设置默认集群策略
     */
    public void setDefaultCluster(String name) {
        if (clusters.containsKey(name)) {
            this.defaultCluster = name;
            logger.info("Default cluster strategy set to: {}", name);
        } else {
            logger.warn("Cannot set default cluster: {} not found", name);
        }
    }

    /**
     * 获取所有策略名称
     */
    public java.util.Set<String> getAvailableClusters() {
        return clusters.keySet();
    }
}