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
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class RandomLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);

    private final Random random = new Random();

    @Override
    public String getName() {
        return "random";
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
            logger.warn("[Random] No available instances for service: {}", serviceName);
            return null;
        }

        if (available.size() == 1) {
            ServiceInstance instance = available.get(0);
            InetSocketAddress address = new InetSocketAddress(instance.getHost(), instance.getPort());
            return SelectionResult.simple(address);
        }

        // Step 2: 计算每个实例的有效权重（静态权重 × 预热权重）
        int[] weights = new int[available.size()];
        int totalWeight = 0;

        for (int i = 0; i < available.size(); i++) {
            int effectiveWeight = available.get(i).getEffectiveWeight();
            weights[i] = effectiveWeight;
            totalWeight += effectiveWeight;
        }

        // Step 3: 加权随机选择
        ServiceInstance selected;

        if (totalWeight <= 0) {
            // 总权重为 0，简单随机
            int index = random.nextInt(available.size());
            selected = available.get(index);
        } else {
            int randomWeight = random.nextInt(totalWeight);
            int cumulative = 0;

            selected = available.get(0); // 默认第一个
            for (int i = 0; i < available.size(); i++) {
                cumulative += weights[i];
                if (randomWeight < cumulative) {
                    selected = available.get(i);
                    break;
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[Random] Selected {}:{} for service {} (weight={})",
                    selected.getHost(), selected.getPort(), serviceName,
                    selected.getEffectiveWeight());
        }

        InetSocketAddress address = new InetSocketAddress(selected.getHost(), selected.getPort());
        return SelectionResult.simple(address);
    }
}