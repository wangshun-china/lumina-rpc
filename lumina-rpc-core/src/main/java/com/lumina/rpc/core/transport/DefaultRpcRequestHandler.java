package com.lumina.rpc.core.transport;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 默认 RPC 请求处理器实现
 */
public class DefaultRpcRequestHandler implements RpcRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRpcRequestHandler.class);

    // 服务注册表
    private final ServiceRegistry serviceRegistry;

    public DefaultRpcRequestHandler(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void handleRequest(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcRequest request = (RpcRequest) msg.getBody();
        if (request == null) {
            logger.error("Received empty request body");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Processing RPC request: requestId={}, interface={}, method={}",
                    request.getRequestId(), request.getInterfaceName(), request.getMethodName());
        }

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
    }

    /**
     * 调用服务
     *
     * @param request RPC 请求
     * @return RPC 响应
     */
    private RpcResponse invokeService(RpcRequest request) {
        try {
            // 从注册表获取服务实现
            Object serviceBean = serviceRegistry.getService(
                    request.getInterfaceName(), request.getVersion());

            if (serviceBean == null) {
                logger.error("Service not found: interface={}, version={}",
                        request.getInterfaceName(), request.getVersion());
                return RpcResponse.error(request.getRequestId(),
                        "Service not found: " + request.getInterfaceName());
            }

            // 获取方法
            Class<?> serviceClass = serviceBean.getClass();
            String methodName = request.getMethodName();
            Class<?>[] parameterTypes = request.getParameterTypes();
            Object[] parameters = request.getParameters();

            // 调用方法
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            Object result = method.invoke(serviceBean, parameters);

            if (logger.isDebugEnabled()) {
                logger.debug("Service method invoked successfully: interface={}, method={}",
                        request.getInterfaceName(), methodName);
            }

            return RpcResponse.success(request.getRequestId(), result);

        } catch (NoSuchMethodException e) {
            logger.error("Method not found: interface={}, method={}",
                    request.getInterfaceName(), request.getMethodName(), e);
            return RpcResponse.error(request.getRequestId(),
                    "Method not found: " + request.getMethodName());
        } catch (Exception e) {
            logger.error("Service invocation failed: interface={}, method={}",
                    request.getInterfaceName(), request.getMethodName(), e);
            return RpcResponse.error(request.getRequestId(),
                    "Service invocation failed: " + e.getMessage());
        }
    }
}
