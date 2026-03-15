package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 最少活跃调用负载均衡器
 *
 * 选择当前活跃请求数最少的实例
 * 适用于长连接场景，能够自动感知后端负载情况
 */
public class LeastActiveLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LeastActiveLoadBalancer.class);

    private final ActiveCounter activeCounter = ActiveCounter.getInstance();

    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, String serviceName) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            logger.warn("No available service addresses for service: {}", serviceName);
            return null;
        }

        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }

        // 查找活跃调用数最少的地址
        InetSocketAddress selected = null;
        int minActive = Integer.MAX_VALUE;

        for (InetSocketAddress address : serviceAddresses) {
            String addressKey = address.getHostString() + ":" + address.getPort();
            int active = activeCounter.getActiveCount(addressKey);

            if (active < minActive) {
                minActive = active;
                selected = address;
            }
        }

        // 如果所有实例活跃数相同，随机选择一个
        if (selected == null) {
            selected = serviceAddresses.get((int) (Math.random() * serviceAddresses.size()));
        }

        if (logger.isDebugEnabled()) {
            String addressKey = selected.getHostString() + ":" + selected.getPort();
            logger.debug("[LeastActiveLoadBalancer] Selected {} for service {} (active={})",
                    addressKey, serviceName, activeCounter.getActiveCount(addressKey));
        }

        return selected;
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

        // 查找活跃调用数最少且权重最高的实例
        ServiceInstance selected = null;
        int minActive = Integer.MAX_VALUE;
        int maxWeight = -1;

        for (ServiceInstance instance : instances) {
            String addressKey = instance.getAddress();
            int active = activeCounter.getActiveCount(addressKey);
            int effectiveWeight = instance.getEffectiveWeight();

            // 优先选择活跃数最少的
            // 如果活跃数相同，选择权重更高的
            if (active < minActive || (active == minActive && effectiveWeight > maxWeight)) {
                minActive = active;
                maxWeight = effectiveWeight;
                selected = instance;
            }
        }

        if (selected == null) {
            selected = instances.get(0);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[LeastActiveLoadBalancer] Selected {} for service {} (active={}, weight={})",
                    selected.getAddress(), serviceName, minActive, maxWeight);
        }

        return new InetSocketAddress(selected.getHost(), selected.getPort());
    }

    @Override
    public String getName() {
        return "least-active";
    }
}