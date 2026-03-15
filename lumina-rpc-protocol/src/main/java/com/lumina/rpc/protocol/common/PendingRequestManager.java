package com.lumina.rpc.protocol.common;

import com.lumina.rpc.protocol.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 待处理请求管理器
 *
 * 维护一个全局的 ConcurrentHashMap，用于将异步的 Netty 响应转为同步的方法返回
 *
 * 支持优雅停机：
 * - 等待所有 pending 请求完成
 * - 超时后强制取消
 */
public class PendingRequestManager {

    private static final Logger logger = LoggerFactory.getLogger(PendingRequestManager.class);

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
     * 等待所有待处理请求完成（用于优雅停机）
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否所有请求都已完成
     */
    public boolean awaitAllPendingRequests(long timeoutMs) {
        int pendingCount = pendingRequests.size();
        if (pendingCount == 0) {
            return true;
        }

        logger.info("⏳ [Graceful Shutdown] Waiting for {} pending requests (timeout: {}ms)...",
                pendingCount, timeoutMs);

        long startTime = System.currentTimeMillis();
        long remainingTime = timeoutMs;

        while (!pendingRequests.isEmpty() && remainingTime > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            remainingTime = timeoutMs - (System.currentTimeMillis() - startTime);
        }

        int remaining = pendingRequests.size();
        if (remaining > 0) {
            logger.warn("⚠️ [Graceful Shutdown] Timeout! Cancelling {} pending requests", remaining);
            // 取消所有未完成的请求
            for (var entry : pendingRequests.entrySet()) {
                entry.getValue().cancel(true);
            }
            pendingRequests.clear();
            return false;
        }

        logger.info("✅ [Graceful Shutdown] All pending requests completed");
        return true;
    }

    /**
     * 清空所有待处理请求
     */
    public void clear() {
        pendingRequests.clear();
    }
}