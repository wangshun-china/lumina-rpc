package com.lumina.rpc.protocol.common;

import com.lumina.rpc.protocol.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待处理请求管理器
 *
 * 维护一个全局的 ConcurrentHashMap，用于将异步的 Netty 响应转为同步的方法返回
 */
public class PendingRequestManager {

    // 单例实例
    private static final PendingRequestManager INSTANCE = new PendingRequestManager();

    // 待处理请求映射: requestId -> CompletableFuture
    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests;

    private PendingRequestManager() {
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    /**
     * 获取单例实例
     *
     * @return PendingRequestManager 实例
     */
    public static PendingRequestManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加待处理请求
     *
     * @param requestId 请求ID
     * @param future    CompletableFuture
     */
    public void addPendingRequest(long requestId, CompletableFuture<RpcResponse> future) {
        pendingRequests.put(requestId, future);
    }

    /**
     * 移除待处理请求
     *
     * @param requestId 请求ID
     * @return 移除的 CompletableFuture，如果不存在返回 null
     */
    public CompletableFuture<RpcResponse> removePendingRequest(long requestId) {
        return pendingRequests.remove(requestId);
    }

    /**
     * 完成待处理请求
     *
     * @param response RPC 响应
     * @return 是否成功完成
     */
    public boolean completePendingRequest(RpcResponse response) {
        if (response == null) {
            return false;
        }
        CompletableFuture<RpcResponse> future = pendingRequests.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
            return true;
        }
        return false;
    }

    /**
     * 以异常完成待处理请求
     *
     * @param requestId 请求ID
     * @param throwable 异常
     * @return 是否成功完成
     */
    public boolean completeExceptionally(long requestId, Throwable throwable) {
        CompletableFuture<RpcResponse> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(throwable);
            return true;
        }
        return false;
    }

    /**
     * 获取待处理请求数量
     *
     * @return 待处理请求数量
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * 清空所有待处理请求
     */
    public void clear() {
        pendingRequests.clear();
    }
}