package com.lumina.rpc.core.spi;

import com.lumina.rpc.core.discovery.ServiceInstance;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 负载均衡器接口
 *
 * 职责：
 * - 从可用实例中选择一个地址
 * - 内部管理状态（如活跃计数）
 * - 过滤已失败的节点
 *
 * 支持预热权重：
 * - 新实例启动后权重从 0 逐渐增加到 1
 * - 避免瞬时高负载导致超时
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public interface LoadBalancer {

    /**
     * 从可用服务地址列表中选择一个地址（旧接口，兼容）
     *
     * @param serviceAddresses 可用服务地址列表
     * @param serviceName      服务名称
     * @return 选中的服务地址
     * @deprecated 使用 selectWithExclusion 替代
     */
    @Deprecated
    default InetSocketAddress select(List<InetSocketAddress> serviceAddresses, String serviceName) {
        SelectionResult result = selectWithExclusion(
                Collections.emptyList(),
                Collections.emptyList(),
                serviceName,
                null);
        return result != null ? result.getAddress() : null;
    }

    /**
     * 从可用服务实例列表中选择一个地址（旧接口，兼容）
     *
     * @param instances   可用服务实例列表
     * @param serviceName 服务名称
     * @return 选中的服务地址
     * @deprecated 使用 selectWithExclusion 替代
     */
    @Deprecated
    default InetSocketAddress selectInstance(List<ServiceInstance> instances, String serviceName) {
        return selectInstance(instances, serviceName, Collections.emptyList());
    }

    /**
     * 从可用服务实例列表中选择一个地址（支持排除失败节点）
     *
     * @param instances   可用服务实例列表
     * @param serviceName 服务名称
     * @param excluded    已失败的地址（需要排除）
     * @return 选中的服务地址
     * @deprecated 使用 selectWithExclusion 替代
     */
    @Deprecated
    default InetSocketAddress selectInstance(List<ServiceInstance> instances,
                                              String serviceName,
                                              List<InetSocketAddress> excluded) {
        SelectionResult result = selectWithExclusion(instances, excluded, serviceName, null);
        return result != null ? result.getAddress() : null;
    }

    /**
     * 从可用服务实例中选择一个地址（核心方法）
     *
     * 负载均衡器内部：
     * - 过滤已失败的节点（excluded）
     * - 执行选择算法
     * - 管理内部状态（如活跃计数）
     * - 返回地址和完成回调
     *
     * @param instances   可用服务实例列表
     * @param excluded    已失败的地址（需要排除）
     * @param serviceName 服务名称
     * @param context     选择上下文（可用于传递 traceId 等）
     * @return SelectionResult 包含地址和完成回调
     */
    SelectionResult selectWithExclusion(
            List<ServiceInstance> instances,
            List<InetSocketAddress> excluded,
            String serviceName,
            Object context);

    /**
     * 获取负载均衡器名称
     *
     * @return 名称
     */
    String getName();

    /**
     * 默认过滤方法：从实例列表中排除失败地址
     *
     * @param instances 原始实例列表
     * @param excluded  需要排除的地址
     * @return 过滤后的可用实例列表
     */
    default List<ServiceInstance> filterExcluded(List<ServiceInstance> instances,
                                                  List<InetSocketAddress> excluded) {
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }

        if (excluded == null || excluded.isEmpty()) {
            return instances;
        }

        List<ServiceInstance> available = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            InetSocketAddress addr = new InetSocketAddress(instance.getHost(), instance.getPort());
            if (!excluded.contains(addr)) {
                available.add(instance);
            }
        }
        return available;
    }
}