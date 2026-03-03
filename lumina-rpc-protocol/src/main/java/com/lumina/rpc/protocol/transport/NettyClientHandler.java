package com.lumina.rpc.protocol.transport;

import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty RPC 客户端处理器
 */
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    // 待处理请求管理器
    private final PendingRequestManager pendingRequestManager;

    public NettyClientHandler() {
        this.pendingRequestManager = PendingRequestManager.getInstance();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Received RPC message: requestId={}, messageType={}",
                    msg.getRequestId(), msg.getMessageType());
        }

        if (msg.isResponse()) {
            // 处理响应
            RpcResponse response = (RpcResponse) msg.getBody();
            if (response != null) {
                boolean completed = pendingRequestManager.completePendingRequest(response);
                if (!completed) {
                    logger.warn("No pending request found for response: requestId={}",
                            response.getRequestId());
                }
            } else {
                logger.warn("Received empty response body for requestId={}", msg.getRequestId());
            }
        } else if (msg.isRequest()) {
            // 客户端不应该收到请求
            logger.warn("Client received unexpected request message: requestId={}", msg.getRequestId());
        } else {
            // 心跳或其他消息
            if (logger.isDebugEnabled()) {
                logger.debug("Received message of type: {}", msg.getMessageType());
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("RPC client channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String addressKey = ctx.channel().remoteAddress() != null
                ? ctx.channel().remoteAddress().toString()
                : "unknown";
        logger.warn("💀 RPC client channel inactive: {}, connection lost", addressKey);

        // 触发连接失效事件，通知上层进行重连
        // 注意：实际的重连逻辑在 NettyClient.getOrConnect() 中处理
        // 这里只是记录日志，让上层知道连接已经断开

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("RPC client exception caught", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 发送心跳包
                if (logger.isDebugEnabled()) {
                    logger.debug("Sending heartbeat to server: {}", ctx.channel().remoteAddress());
                }
                RpcMessage heartbeat = new RpcMessage();
                heartbeat.setMagicNumber(RpcMessage.MAGIC_NUMBER);
                heartbeat.setVersion(RpcMessage.VERSION);
                heartbeat.setSerializerType(RpcMessage.JSON);
                heartbeat.setMessageType(RpcMessage.HEARTBEAT);
                heartbeat.setRequestId(0);
                heartbeat.setDataLength(0);
                ctx.writeAndFlush(heartbeat);
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}