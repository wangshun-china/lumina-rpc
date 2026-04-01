package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 *
 * 支持服务预热：
 * - 新实例启动后，权重从 0 逐渐增加到 1
 * - 预热期间的实例被选中的概率更低
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    // 每个服务的轮询计数器
    private final ConcurrentHashMap<String, AtomicInteger> serviceCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "round-robin";
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
            logger.warn("[RoundRobin] No available instances for service: {}", serviceName);
            return null;
        }

        if (available.size() == 1) {
            ServiceInstance instance = available.get(0);
            InetSocketAddress address = new InetSocketAddress(instance.getHost(), instance.getPort());
            return SelectionResult.simple(address);
        }

        // Step 2: 计算每个实例的预热权重
        double[] weights = new double[available.size()];
        double totalWeight = 0.0;

        for (int i = 0; i < available.size(); i++) {
            ServiceInstance instance = available.get(i);
            double weight = instance.getWarmupWeight();
            weights[i] = weight;
            totalWeight += weight;

            if (logger.isDebugEnabled() && instance.isInWarmup()) {
                logger.debug("[Warmup] Instance {}:{} weight={:.2f}, progress={}%",
                        instance.getHost(), instance.getPort(), weight, instance.getWarmupProgress());
            }
        }

        // Step 3: 加权随机选择
        if (totalWeight <= 0) {
            totalWeight = available.size();
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1.0;
            }
        }

        double random = Math.random() * totalWeight;
        double cumulative = 0.0;

        ServiceInstance selected = available.get(0);
        for (int i = 0; i < available.size(); i++) {
            cumulative += weights[i];
            if (random < cumulative) {
                selected = available.get(i);
                break;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[RoundRobin] Selected {}:{} for service {} (warmup={})",
                    selected.getHost(), selected.getPort(), serviceName,
                    selected.isInWarmup());
        }

        InetSocketAddress address = new InetSocketAddress(selected.getHost(), selected.getPort());
        return SelectionResult.simple(address);
    }
}