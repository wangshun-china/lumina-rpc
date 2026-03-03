package com.lumina.rpc.core.transport;

import com.lumina.rpc.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty RPC 服务器处理器
 */
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    // 请求处理器
    private final RpcRequestHandler requestHandler;

    public NettyServerHandler(RpcRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Received RPC message: requestId={}, messageType={}",
                    msg.getRequestId(), msg.getMessageType());
        }

        if (msg.isRequest()) {
            // 处理请求
            handleRequest(ctx, msg);
        } else if (msg.isResponse()) {
            // 服务器不应该收到响应
            logger.warn("Server received unexpected response message: requestId={}", msg.getRequestId());
        } else {
            // 心跳或其他消息
            handleHeartbeat(ctx, msg);
        }
    }

    /**
     * 处理 RPC 请求
     *
     * @param ctx ChannelHandlerContext
     * @param msg RPC 消息
     */
    private void handleRequest(ChannelHandlerContext ctx, RpcMessage msg) {
        // 提交到线程池异步处理
        requestHandler.handleRequest(ctx, msg);
    }

    /**
     * 处理心跳消息
     *
     * @param ctx ChannelHandlerContext
     * @param msg RPC 消息
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, RpcMessage msg) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received heartbeat from client: {}", ctx.channel().remoteAddress());
        }
        // 可以发送心跳响应
        RpcMessage heartbeatResponse = new RpcMessage();
        heartbeatResponse.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        heartbeatResponse.setVersion(RpcMessage.VERSION);
        heartbeatResponse.setSerializerType(RpcMessage.JSON);
        heartbeatResponse.setMessageType(RpcMessage.HEARTBEAT);
        heartbeatResponse.setRequestId(msg.getRequestId());
        heartbeatResponse.setDataLength(0);
        ctx.writeAndFlush(heartbeatResponse);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("RPC client channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("RPC client channel inactive: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("RPC server handler exception caught", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读超时，关闭连接
                logger.warn("Read idle timeout, closing channel: {}", ctx.channel().remoteAddress());
                ctx.close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                // 写超时，发送心跳
                if (logger.isDebugEnabled()) {
                    logger.debug("Write idle, sending heartbeat to client: {}",
                            ctx.channel().remoteAddress());
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
