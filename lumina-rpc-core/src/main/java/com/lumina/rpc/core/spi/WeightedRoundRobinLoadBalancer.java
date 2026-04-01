package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平滑加权轮询负载均衡器（Nginx 算法）
 *
 * 特点：
 * 1. 权重大的实例被选中的次数多（符合预期）
 * 2. 不会连续选中同一个实例（平滑分散）
 * 3. 支持预热权重：预热中的实例 effectiveWeight 低，自然少被选中
 * 4. 不需要状态管理，回调为空
 *
 * 算法原理：
 * 1. 每次所有实例 currentWeight += effectiveWeight
 * 2. 选中 currentWeight 最大的实例
 * 3. 被选中的实例 currentWeight -= totalWeight
 * 4. 权重大的实例积分涨得快，更容易被选中
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
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
    public String getName() {
        return "weighted-round-robin";
    }

    @Override
    public SelectionResult selectWithExclusion(
            List<ServiceInstance> instances,
            List<InetSocketAddress> excluded,
            String serviceName,
            Object context) {

        // Step 1: 过滤已失败的节点
        List<ServiceInstance> available = filterExcluded(instances, excluded);

        if (available.isEmpty()) {
            logger.warn("[WeightedRoundRobin] No available instances for service: {}", serviceName);
            return null;
        }

        if (available.size() == 1) {
            ServiceInstance instance = available.get(0);
            InetSocketAddress address = new InetSocketAddress(instance.getHost(), instance.getPort());
            // 不需要状态管理，回调为空
            return SelectionResult.simple(address);
        }

        // Step 2: 平滑加权轮询核心算法
        ServiceInstance selected = doSmoothWeightedSelect(available, serviceName);

        if (selected == null) {
            // 兜底：随机选择
            selected = available.get((int) (Math.random() * available.size()));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[WeightedRoundRobin] Selected {}:{} for service {} (weight={})",
                    selected.getHost(), selected.getPort(), serviceName,
                    selected.getEffectiveWeight());
        }

        InetSocketAddress address = new InetSocketAddress(selected.getHost(), selected.getPort());
        // 不需要状态管理，回调为空
        return SelectionResult.simple(address);
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
                logger.debug("[WeightedRoundRobin] {} currentWeight after select: {}",
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
}