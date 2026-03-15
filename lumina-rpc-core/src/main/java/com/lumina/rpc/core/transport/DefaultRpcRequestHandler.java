package com.lumina.rpc.core.transport;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.trace.TraceContext;
import com.lumina.rpc.core.shutdown.GracefulShutdownManager;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;

/**
 * 默认 RPC 请求处理器实现
 *
 * 集成优雅停机：
 * - 检查停机状态，拒绝新请求
 * - 跟踪正在处理的请求数量
 */
public class DefaultRpcRequestHandler implements RpcRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRpcRequestHandler.class);

    // 服务注册表
    private final ServiceRegistry serviceRegistry;

    // 优雅停机管理器
    private final GracefulShutdownManager shutdownManager;

    public DefaultRpcRequestHandler(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.shutdownManager = GracefulShutdownManager.getInstance();
    }

    @Override
    public void handleRequest(ChannelHandlerContext ctx, RpcMessage msg) {
        // ========== 优雅停机检查 ==========
        if (shutdownManager.isShuttingDown()) {
            logger.warn("[Graceful Shutdown] Rejecting new request - server is shutting down");
            // 发送拒绝响应
            RpcResponse response = RpcResponse.error(msg.getRequestId(), "Server is shutting down");
            RpcMessage responseMessage = new RpcMessage();
            responseMessage.setMagicNumber(RpcMessage.MAGIC_NUMBER);
            responseMessage.setVersion(RpcMessage.VERSION);
            responseMessage.setSerializerType(msg.getSerializerType());
            responseMessage.setMessageType(RpcMessage.RESPONSE);
            responseMessage.setRequestId(msg.getRequestId());
            responseMessage.setBody(response);
            ctx.writeAndFlush(responseMessage);
            return;
        }

        // 记录请求开始
        if (!shutdownManager.onRequestStart()) {
            logger.warn("[Graceful Shutdown] Failed to start request - server is shutting down");
            return;
        }

        RpcRequest request = (RpcRequest) msg.getBody();
        if (request == null) {
            shutdownManager.onRequestEnd();
            logger.error("Received empty request body");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Processing RPC request: requestId={}, interface={}, method={}",
                    request.getRequestId(), request.getInterfaceName(), request.getMethodName());
        }

        try {
            // 调用服务
            RpcResponse response = invokeService(request);

            // 构建响应消息
            RpcMessage responseMessage = new RpcMessage();
            responseMessage.setMagicNumber(RpcMessage.MAGIC_NUMBER);
            responseMessage.setVersion(RpcMessage.VERSION);
            responseMessage.setSerializerType(msg.getSerializerType());
            responseMessage.setMessageType(RpcMessage.RESPONSE);
            responseMessage.setRequestId(request.getRequestId());
            responseMessage.setBody(response);

            // 发送响应
            ctx.writeAndFlush(responseMessage).addListener(future -> {
                if (!future.isSuccess()) {
                    logger.error("Failed to send RPC response", future.cause());
                }
            });
        } finally {
            // 记录请求结束
            shutdownManager.onRequestEnd();
        }
    }

    /**
     * 调用服务
     *
     * @param request RPC 请求
     * @return RPC 响应
     */
    private RpcResponse invokeService(RpcRequest request) {
        // 提取并设置 Trace ID 到上下文
        String traceId = request.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            TraceContext.setTraceId(traceId);
            MDC.put("traceId", traceId);
        }

        try {
            // 从注册表获取服务实现
            Object serviceBean = serviceRegistry.getService(
                    request.getInterfaceName(), request.getVersion());

            if (serviceBean == null) {
                logger.error("[Trace:{}] Service not found: interface={}, version={}",
                        traceId, request.getInterfaceName(), request.getVersion());
                return RpcResponse.error(request.getRequestId(), traceId,
                        "Service not found: " + request.getInterfaceName());
            }

            // 获取方法
            Class<?> serviceClass = serviceBean.getClass();
            String methodName = request.getMethodName();
            Class<?>[] parameterTypes = request.getParameterTypes();
            Object[] parameters = request.getParameters();

            if (logger.isDebugEnabled()) {
                logger.debug("[Trace:{}] Processing: {}.{}, params={}",
                        traceId, request.getInterfaceName(), methodName, parameters);
            }

            // 调用方法
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            Object result = method.invoke(serviceBean, parameters);

            if (logger.isDebugEnabled()) {
                logger.debug("[Trace:{}] Service method invoked successfully: interface={}, method={}",
                        traceId, request.getInterfaceName(), methodName);
            }

            return RpcResponse.success(request.getRequestId(), traceId, result);

        } catch (NoSuchMethodException e) {
            logger.error("[Trace:{}] Method not found: interface={}, method={}",
                    traceId, request.getInterfaceName(), request.getMethodName(), e);
            return RpcResponse.error(request.getRequestId(), traceId,
                    "Method not found: " + request.getMethodName());
        } catch (Exception e) {
            logger.error("[Trace:{}] Service invocation failed: interface={}, method={}",
                    traceId, request.getInterfaceName(), request.getMethodName(), e);
            return RpcResponse.error(request.getRequestId(), traceId,
                    "Service invocation failed: " + e.getMessage());
        } finally {
            // 清理上下文
            TraceContext.clear();
            MDC.remove("traceId");
        }
    }
}
