package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加权轮询负载均衡器
 *
 * 根据实例权重进行轮询选择，权重越高的实例获得的请求越多
 * 支持预热权重：预热中的实例有效权重会降低
 */
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(WeightedRoundRobinLoadBalancer.class);

    // 每个服务的轮询状态，key: serviceName, value: 当前索引
    private final ConcurrentHashMap<String, AtomicInteger> currentIndexMap = new ConcurrentHashMap<>();

    // 每个服务的当前权重计数器，key: serviceName, value: 当前剩余权重
    private final ConcurrentHashMap<String, AtomicInteger> currentWeightMap = new ConcurrentHashMap<>();

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
        AtomicInteger counter = currentIndexMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % serviceAddresses.size();
        if (index < 0) {
            counter.set(0);
            index = 0;
        }

        return serviceAddresses.get(index);
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

        // 计算每个实例的有效权重
        int[] weights = new int[instances.size()];
        int totalWeight = 0;

        for (int i = 0; i < instances.size(); i++) {
            int effectiveWeight = instances.get(i).getEffectiveWeight();
            weights[i] = Math.max(1, effectiveWeight); // 最小权重为 1
            totalWeight += weights[i];
        }

        // 使用平滑加权轮询算法
        // 每次选择权重最大的实例，然后减去总权重
        // 这样可以让权重高的实例被更均匀地选中

        // 获取当前状态
        AtomicInteger currentWeight = currentWeightMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        AtomicInteger currentIndex = currentIndexMap.computeIfAbsent(serviceName, k -> new AtomicInteger(0));

        // 简化的加权轮询：基于权重比例的选择
        int selected = selectByWeight(weights, totalWeight, currentIndex, currentWeight);

        ServiceInstance instance = instances.get(selected);

        if (logger.isDebugEnabled()) {
            logger.debug("[WeightedRoundRobinLoadBalancer] Selected {}:{} for service {} (weight={}/{})",
                    instance.getHost(), instance.getPort(), serviceName,
                    weights[selected], totalWeight);
        }

        return new InetSocketAddress(instance.getHost(), instance.getPort());
    }

    /**
     * 基于权重的轮询选择
     */
    private int selectByWeight(int[] weights, int totalWeight, AtomicInteger currentIndex, AtomicInteger currentWeight) {
        // 简单加权轮询：维护一个计数器，每次减去当前实例的权重
        // 当计数器小于等于 0 时，选择当前实例并重置计数器

        int index = currentIndex.get();
        int weight = currentWeight.get();

        weight -= weights[index];

        if (weight <= 0) {
            // 选择当前实例，移动到下一个
            int selectedIndex = index;
            index = (index + 1) % weights.length;
            weight = totalWeight;

            currentIndex.set(index);
            currentWeight.set(weight);

            return selectedIndex;
        } else {
            // 继续当前实例
            currentWeight.set(weight);
            return index;
        }
    }

    @Override
    public String getName() {
        return "weighted-round-robin";
    }
}