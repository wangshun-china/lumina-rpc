package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

/**
 * 最少活跃调用负载均衡器
 *
 * 选择当前活跃请求数最少的实例
 * 内部管理 ActiveCounter 状态
 *
 * 特点：
 * - 自动感知后端负载情况
 * - 活跃数小的节点优先被选中
 * - 活跃数相同时选择权重高的
 * - 通过回调机制清理活跃计数
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class LeastActiveLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(LeastActiveLoadBalancer.class);

    private final ActiveCounter activeCounter = ActiveCounter.getInstance();

    @Override
    public String getName() {
        return "least-active";
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
            logger.warn("[LeastActive] No available instances for service: {}", serviceName);
            return null;
        }

        if (available.size() == 1) {
            ServiceInstance instance = available.get(0);
            InetSocketAddress address = new InetSocketAddress(instance.getHost(), instance.getPort());
            String addressKey = instance.getAddress();

            // 增加活跃数
            activeCounter.increment(addressKey);

            // 创建回调（请求完成时减少活跃数）
            Runnable callback = () -> {
                activeCounter.decrement(addressKey);
                logger.debug("[LeastActive] Request completed, active count decremented for: {}", addressKey);
            };

            return new SelectionResult(address, callback);
        }

        // Step 2: 找活跃数最少且权重最高的实例
        ServiceInstance selected = null;
        int minActive = Integer.MAX_VALUE;
        int maxWeight = -1;

        for (ServiceInstance instance : available) {
            String addressKey = instance.getAddress();
            int active = activeCounter.getActiveCount(addressKey);
            int effectiveWeight = instance.getEffectiveWeight();

            // 优先选择活跃数少的
            // 活跃数相同则选择权重高的
            if (active < minActive || (active == minActive && effectiveWeight > maxWeight)) {
                minActive = active;
                maxWeight = effectiveWeight;
                selected = instance;
            }
        }

        if (selected == null) {
            selected = available.get(0);
        }

        // Step 3: 增加活跃数
        String selectedKey = selected.getAddress();
        activeCounter.increment(selectedKey);

        InetSocketAddress selectedAddress = new InetSocketAddress(selected.getHost(), selected.getPort());

        // Step 4: 创建完成回调
        Runnable callback = () -> {
            activeCounter.decrement(selectedKey);
            logger.debug("[LeastActive] Request completed for {}, active count now: {}",
                    selectedKey, activeCounter.getActiveCount(selectedKey));
        };

        if (logger.isDebugEnabled()) {
            logger.debug("[LeastActive] Selected {} for service {} (active={}, weight={})",
                    selectedKey, serviceName, minActive, maxWeight);
        }

        return new SelectionResult(selectedAddress, callback);
    }
}