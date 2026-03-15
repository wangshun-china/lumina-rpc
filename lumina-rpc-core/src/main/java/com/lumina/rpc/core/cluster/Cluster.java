package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.discovery.ServiceInstance;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 集群容错策略接口
 *
 * 对标 Dubbo 的 Cluster 接口，提供多种容错模式：
 * - failover: 失败自动重试其他服务器
 * - failfast: 快速失败，只发起一次调用
 * - failsafe: 失败安全，异常直接忽略
 * - forking: 并行调用，一个成功即返回
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public interface Cluster {

    /**
     * 获取集群策略名称
     */
    String getName();

    /**
     * 执行调用
     *
     * @param invocation 调用上下文
     * @return 调用结果
     * @throws Exception 调用异常
     */
    Object invoke(ClusterInvocation invocation) throws Throwable;
}