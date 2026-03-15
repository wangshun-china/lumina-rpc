package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/**
 * 加权随机负载均衡器
 *
 * 根据实例权重进行随机选择，权重越高的实例被选中的概率越大
 * 支持预热权重：预热中的实例有效权重会降低
 */
public class RandomLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);

    private final Random random = new Random();

    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, String serviceName) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            logger.warn("No available service addresses for service: {}", serviceName);
            return null;
        }

        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }

        // 简单随机选择
        int index = random.nextInt(serviceAddresses.size());
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

        // 计算每个实例的有效权重（静态权重 × 预热权重）
        int[] weights = new int[instances.size()];
        int totalWeight = 0;

        for (int i = 0; i < instances.size(); i++) {
            int effectiveWeight = instances.get(i).getEffectiveWeight();
            weights[i] = effectiveWeight;
            totalWeight += effectiveWeight;
        }

        // 如果总权重为 0，给所有实例相等的权重
        if (totalWeight <= 0) {
            int index = random.nextInt(instances.size());
            ServiceInstance selected = instances.get(index);
            return new InetSocketAddress(selected.getHost(), selected.getPort());
        }

        // 加权随机选择
        int randomWeight = random.nextInt(totalWeight);
        int cumulative = 0;

        for (int i = 0; i < instances.size(); i++) {
            cumulative += weights[i];
            if (randomWeight < cumulative) {
                ServiceInstance selected = instances.get(i);

                if (logger.isDebugEnabled()) {
                    logger.debug("[RandomLoadBalancer] Selected {}:{} for service {} (weight={}/{})",
                            selected.getHost(), selected.getPort(), serviceName,
                            weights[i], totalWeight);
                }

                return new InetSocketAddress(selected.getHost(), selected.getPort());
            }
        }

        // 兜底：返回最后一个实例
        ServiceInstance last = instances.get(instances.size() - 1);
        return new InetSocketAddress(last.getHost(), last.getPort());
    }

    @Override
    public String getName() {
        return "random";
    }
}