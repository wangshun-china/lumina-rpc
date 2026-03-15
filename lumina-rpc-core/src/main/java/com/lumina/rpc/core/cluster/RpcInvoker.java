package com.lumina.rpc.core.cluster;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RPC 调用工具类
 *
 * 执行单次 RPC 调用的底层逻辑
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class RpcInvoker {

    private static final Logger logger = LoggerFactory.getLogger(RpcInvoker.class);

    /**
     * 执行单次 RPC 调用
     *
     * @param address   目标地址
     * @param request   RPC 请求
     * @param serializer 序列化器
     * @param nettyClient Netty 客户端
     * @param timeout   超时时间
     * @return RPC 响应
     */
    public static RpcResponse invoke(InetSocketAddress address, RpcRequest request,
                                     Serializer serializer, NettyClient nettyClient,
                                     long timeout) throws Throwable {

        PendingRequestManager pendingManager = PendingRequestManager.getInstance();

        // 构建 RPC 消息
        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(serializer.getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        // 注册待处理请求
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        pendingManager.addPendingRequest(request.getRequestId(), responseFuture);

        try {
            // 发送请求
            nettyClient.sendMessage(address, message);

            // 等待响应
            RpcResponse response = responseFuture.get(timeout, TimeUnit.MILLISECONDS);

            if (response == null) {
                throw new RuntimeException("RPC response is null");
            }

            return response;

        } catch (Exception e) {
            // 移除待处理请求
            pendingManager.removePendingRequest(request.getRequestId());

            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }

    /**
     * 执行异步 RPC 调用
     */
    public static CompletableFuture<RpcResponse> invokeAsync(InetSocketAddress address, RpcRequest request,
                                                              Serializer serializer, NettyClient nettyClient,
                                                              long timeout) {

        PendingRequestManager pendingManager = PendingRequestManager.getInstance();

        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(serializer.getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        pendingManager.addPendingRequest(request.getRequestId(), responseFuture);

        try {
            nettyClient.sendMessage(address, message);
        } catch (Exception e) {
            pendingManager.removePendingRequest(request.getRequestId());
            responseFuture.completeExceptionally(e);
            return responseFuture;
        }

        // 设置超时
        responseFuture.orTimeout(timeout, TimeUnit.MILLISECONDS)
                .whenComplete((resp, ex) -> {
                    pendingManager.removePendingRequest(request.getRequestId());
                    if (ex != null) {
                        logger.warn("RPC call failed: {}", ex.getMessage());
                    }
                });

        return responseFuture;
    }
}