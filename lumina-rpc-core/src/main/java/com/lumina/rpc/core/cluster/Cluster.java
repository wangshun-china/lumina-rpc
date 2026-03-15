package com.lumina.rpc.core.cluster;

import com.lumina.rpc.core.discovery.ServiceInstance;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 执行同步调用
     *
     * @param invocation 调用上下文
     * @return 调用结果
     * @throws Exception 调用异常
     */
    Object invoke(ClusterInvocation invocation) throws Throwable;

    /**
     * 执行异步调用
     *
     * 异步调用同样走集群容错策略（熔断、限流、重试）
     *
     * @param invocation 调用上下文
     * @return CompletableFuture 包装的调用结果
     */
    default CompletableFuture<Object> invokeAsync(ClusterInvocation invocation) {
        // 默认实现：用线程池包装同步调用
        CompletableFuture<Object> future = new CompletableFuture<>();
        try {
            Object result = invoke(invocation);
            future.complete(result);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}