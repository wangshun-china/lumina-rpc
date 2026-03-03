package com.lumina.rpc.core.transport;

import com.lumina.rpc.protocol.RpcMessage;
import io.netty.channel.ChannelHandlerContext;

/**
 * RPC 请求处理器接口
 *
 * 定义处理 RPC 请求的方法
 */
public interface RpcRequestHandler {

    /**
     * 处理 RPC 请求
     *
     * @param ctx ChannelHandlerContext
     * @param msg RPC 消息
     */
    void handleRequest(ChannelHandlerContext ctx, RpcMessage msg);
}
