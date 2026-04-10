package com.lumina.rpc.protocol.common;

import com.lumina.rpc.protocol.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待处理请求管理器
 *
 * 用于将异步的 Netty 响应转为同步的方法返回
 */
public class PendingRequestManager {

    private static final Logger logger = LoggerFactory.getLogger(PendingRequestManager.class);

    private static final PendingRequestManager INSTANCE = new PendingRequestManager();

    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests;

    private PendingRequestManager() {
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    public static PendingRequestManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加待处理请求
     */
    public void addPendingRequest(long requestId, CompletableFuture<RpcResponse> future) {
        pendingRequests.put(requestId, future);
    }

    /**
     * 移除待处理请求
     */
    public CompletableFuture<RpcResponse> removePendingRequest(long requestId) {
        return pendingRequests.remove(requestId);
    }

    /**
     * 完成待处理请求（收到响应时调用）
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
        logger.warn("No pending request found for response: requestId={}", response.getRequestId());
        return false;
    }
}