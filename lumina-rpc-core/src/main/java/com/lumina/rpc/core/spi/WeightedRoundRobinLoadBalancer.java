package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平滑加权轮询负载均衡器（Nginx 算法）
 *
 * 特点：
 * 1. 权重大的实例被选中的次数多（符合预期）
 * 2. 不会连续选中同一个实例（平滑分散）
 * 3. 支持预热权重：预热中的实例 effectiveWeight 低，自然少被选中
 *
 * 算法原理：
 * 1. 每次所有实例 currentWeight += effectiveWeight
 * 2. 选中 currentWeight 最大的实例
 * 3. 被选中的实例 currentWeight -= totalWeight
 * 4. 权重大的实例积分涨得快，更容易被选中
 */
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(WeightedRoundRobinLoadBalancer.class);

    /**
     * 每个服务的当前权重状态
     * key: serviceName#instanceAddress
     * value: 当前积分
     */
    private final ConcurrentHashMap<String, Integer> currentWeights = new ConcurrentHashMap<>();

    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, String serviceName) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            logger.warn("No available service addresses for service: {}", serviceName);
            return null;
        }

        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }

        // 简单轮询（无权重信息时）
        return simpleRoundRobin(serviceAddresses, serviceName);
    }

    @Override
    public InetSocketAddress selectInstance(List<ServiceInstance> instances, String serviceName) {
        if (instances == null || instances.isEmpty()) {
            logger.warn("No available service instances for service: {}", serviceName);
            return null;
        }

        if (instances.size() == 1) {
            ServiceInstance instance = instances.get(0);
            return new InetSocketAddress(instance.getHost(), instance.getPort());
        }

        // 平滑加权轮询核心算法
        ServiceInstance selected = doSmoothWeightedSelect(instances, serviceName);

        if (selected == null) {
            // 兜底：简单随机
            selected = instances.get((int) (Math.random() * instances.size()));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[WeightedRoundRobinLoadBalancer] Selected {}:{} for service {} (weight={})",
                    selected.getHost(), selected.getPort(), serviceName,
                    selected.getEffectiveWeight());
        }

        return new InetSocketAddress(selected.getHost(), selected.getPort());
    }

    /**
     * 平滑加权轮询核心实现（Nginx 算法）
     *
     * 算法步骤：
     * 1. 每个实例 currentWeight += effectiveWeight
     * 2. 选中 currentWeight 最大的实例
     * 3. 被选中的实例 currentWeight -= totalWeight
     * 4. 返回选中的实例
     */
    private ServiceInstance doSmoothWeightedSelect(List<ServiceInstance> instances, String serviceName) {
        int totalWeight = 0;
        ServiceInstance selected = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        // 第一轮：给每个实例加积分，找最大的
        for (ServiceInstance instance : instances) {
            // 使用 effectiveWeight（包含预热权重！）
            int effectiveWeight = instance.getEffectiveWeight();
            totalWeight += effectiveWeight;

            // 构建 key：服务名 + 实例地址
            String key = buildKey(serviceName, instance);

            // 当前积分 += 有效权重
            int currentWeight = currentWeights.getOrDefault(key, 0) + effectiveWeight;
            currentWeights.put(key, currentWeight);

            // 找积分最高的
            if (currentWeight > maxCurrentWeight) {
                maxCurrentWeight = currentWeight;
                selected = instance;
            }
        }

        // 第二轮：被选中的实例积分 -= 总权重
        if (selected != null && totalWeight > 0) {
            String selectedKey = buildKey(serviceName, selected);
            int newWeight = currentWeights.get(selectedKey) - totalWeight;
            currentWeights.put(selectedKey, newWeight);

            if (logger.isDebugEnabled()) {
                logger.debug("[WeightedRoundRobinLoadBalancer] {} currentWeight after select: {}",
                        selectedKey, newWeight);
            }
        }

        return selected;
    }

    /**
     * 构建缓存 key
     */
    private String buildKey(String serviceName, ServiceInstance instance) {
        return serviceName + "#" + instance.getHost() + ":" + instance.getPort();
    }

    /**
     * 简单轮询（无权重时兜底）
     */
    private InetSocketAddress simpleRoundRobin(List<InetSocketAddress> addresses, String serviceName) {
        // 使用当前时间作为简单轮询索引
        int index = (int) (System.currentTimeMillis() % addresses.size());
        return addresses.get(index);
    }

    @Override
    public String getName() {
        return "weighted-round-robin";
    }
}
