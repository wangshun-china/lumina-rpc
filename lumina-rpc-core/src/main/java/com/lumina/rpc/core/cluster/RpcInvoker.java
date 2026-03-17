package com.lumina.rpc.core.cluster;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.pool.ChannelPoolManager;
import com.lumina.rpc.protocol.spi.SerializerManager;
import com.lumina.rpc.protocol.transport.NettyClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RPC 调用工具类
 *
 * 执行单次 RPC 调用的底层逻辑
 * 集成连接池，实现 Channel 复用
 * 使用默认序列化器（KRYO）进行消息编码
 *
 * @author Lumina-RPC Team
 * @since 1.2.0
 */
public class RpcInvoker {

    private static final Logger logger = LoggerFactory.getLogger(RpcInvoker.class);

    /** 是否启用连接池（默认启用） */
    private static volatile boolean poolEnabled = true;

    /**
     * 设置是否启用连接池
     */
    public static void setPoolEnabled(boolean enabled) {
        poolEnabled = enabled;
        logger.info("🔌 Connection pool {}", enabled ? "enabled" : "disabled");
    }

    /**
     * 执行单次 RPC 调用（使用连接池）
     *
     * @param address   目标地址
     * @param request   RPC 请求
     * @param nettyClient Netty 客户端
     * @param timeout   超时时间
     * @return RPC 响应
     */
    public static RpcResponse invoke(InetSocketAddress address, RpcRequest request,
                                     NettyClient nettyClient, long timeout) throws Throwable {

        PendingRequestManager pendingManager = PendingRequestManager.getInstance();
        ChannelPoolManager poolManager = ChannelPoolManager.getInstance();

        // 构建 RPC 消息，使用默认序列化器类型
        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(SerializerManager.getDefaultSerializer().getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        // 注册待处理请求
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        pendingManager.addPendingRequest(request.getRequestId(), responseFuture);

        Channel channel = null;
        boolean usePool = poolEnabled;

        try {
            // 获取 Channel（优先使用连接池）
            if (usePool) {
                try {
                    channel = poolManager.acquire(address);
                    logger.debug("🔌 Acquired channel from pool for {}", address);
                } catch (Exception e) {
                    // 连接池获取失败，降级为直接连接
                    logger.warn("⚠️ Pool acquire failed, fallback to direct connection: {}", e.getMessage());
                    usePool = false;
                }
            }

            // 降级：直接使用 NettyClient 获取连接
            if (channel == null) {
                channel = nettyClient.getOrConnect(address);
            }

            // 发送请求
            channel.writeAndFlush(message).addListener(future -> {
                if (!future.isSuccess()) {
                    logger.error("Failed to send RPC message to {}", address, future.cause());
                }
            });

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

        } finally {
            // 归还 Channel 到连接池
            if (usePool && channel != null && channel.isActive()) {
                poolManager.release(address, channel);
                logger.debug("📤 Released channel to pool for {}", address);
            }
        }
    }

    /**
     * 执行异步 RPC 调用（使用连接池）
     */
    public static CompletableFuture<RpcResponse> invokeAsync(InetSocketAddress address, RpcRequest request,
                                                              NettyClient nettyClient, long timeout) {

        PendingRequestManager pendingManager = PendingRequestManager.getInstance();
        ChannelPoolManager poolManager = ChannelPoolManager.getInstance();

        // 构建 RPC 消息，使用默认序列化器类型
        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(SerializerManager.getDefaultSerializer().getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        pendingManager.addPendingRequest(request.getRequestId(), responseFuture);

        Channel channel = null;
        boolean usePool = poolEnabled;

        try {
            // 获取 Channel（优先使用连接池）
            if (usePool) {
                try {
                    channel = poolManager.acquire(address);
                } catch (Exception e) {
                    logger.warn("⚠️ Pool acquire failed, fallback to direct connection: {}", e.getMessage());
                    usePool = false;
                }
            }

            if (channel == null) {
                channel = nettyClient.getOrConnect(address);
            }

            final Channel finalChannel = channel;
            final boolean finalUsePool = usePool;

            channel.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    pendingManager.removePendingRequest(request.getRequestId());
                    responseFuture.completeExceptionally(future.cause());
                }
                // 归还 Channel
                if (finalUsePool && finalChannel.isActive()) {
                    poolManager.release(address, finalChannel);
                }
            });

        } catch (Exception e) {
            pendingManager.removePendingRequest(request.getRequestId());
            responseFuture.completeExceptionally(e);
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

    /**
     * 获取连接池状态
     */
    public static String getPoolStatus() {
        return ChannelPoolManager.getInstance().getAllPoolStatus();
    }

    /**
     * 获取全局连接数
     */
    public static int getGlobalChannelCount() {
        return ChannelPoolManager.getInstance().getGlobalChannelCount();
    }
}