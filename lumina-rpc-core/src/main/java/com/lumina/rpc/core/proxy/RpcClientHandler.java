package com.lumina.rpc.core.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.common.RequestIdGenerator;
import com.lumina.rpc.core.discovery.ServiceDiscovery;
import com.lumina.rpc.core.discovery.ServiceInstance;
import com.lumina.rpc.core.exception.NoProviderAvailableException;
import com.lumina.rpc.core.mock.MockRule;
import com.lumina.rpc.core.mock.MockRuleManager;
import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.spi.JsonSerializer;
import com.lumina.rpc.core.spi.LoadBalancer;
import com.lumina.rpc.core.spi.LoadBalancerManager;
import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * RPC 客户端动态代理处理器
 *
 * 拦截被 @LuminaReference 标注的接口方法调用，封装为 RpcRequest 并发送
 * 完整流程：服务发现 -> 负载均衡 -> 连接池获取 Channel -> 发送请求
 *
 * 企业级 Mock 特性：
 * 1. 条件匹配：只有符合条件的调用才触发 Mock
 * 2. 双模引擎：SHORT_CIRCUIT（直接阻断）和 TAMPER（篡改真实数据）
 */
public class RpcClientHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    // 服务接口类
    private final Class<?> interfaceClass;

    // 服务版本号
    private final String version;

    // 超时时间（毫秒）
    private final long timeout;

    // 序列化器
    private final Serializer serializer;

    // ObjectMapper（用于类型转换兜底）
    private final ObjectMapper objectMapper;

    // Netty 客户端
    private final NettyClient nettyClient;

    // 负载均衡器
    private final LoadBalancer loadBalancer;

    // 请求ID生成器
    private final RequestIdGenerator requestIdGenerator;

    // 待处理请求管理器
    private final PendingRequestManager pendingRequestManager;

    // Mock 规则管理器（用于短路拦截）
    private final MockRuleManager mockRuleManager;

    public RpcClientHandler(Class<?> interfaceClass, String version, long timeout,
                            Serializer serializer, NettyClient nettyClient) {
        this.interfaceClass = interfaceClass;
        this.version = version != null ? version : "";
        this.timeout = timeout > 0 ? timeout : 5000;
        this.serializer = serializer;
        this.nettyClient = nettyClient;
        this.loadBalancer = LoadBalancerManager.getDefaultLoadBalancer();
        this.requestIdGenerator = RequestIdGenerator.getInstance();
        this.pendingRequestManager = PendingRequestManager.getInstance();
        // 获取 ObjectMapper（用于类型转换兜底）
        this.objectMapper = (serializer instanceof JsonSerializer)
                ? ((JsonSerializer) serializer).getObjectMapper()
                : null;
        // 获取 Mock 规则管理器
        this.mockRuleManager = MockRuleManager.getInstance();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的方法（如 toString, hashCode, equals）
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String serviceName = interfaceClass.getName();
        String methodName = method.getName();

        // ========== 企业级 Mock 引擎：条件匹配 + 双模处理 ==========
        MockRule matchedRule = mockRuleManager.getMatchingRule(serviceName, methodName, args);

        if (matchedRule != null) {
            // 条件已匹配，根据 Mock 类型处理
            if (matchedRule.isShortCircuit()) {
                // 短路模式：直接返回 Mock 数据，不发起网络请求（传入 args 用于条件匹配）
                return mockRuleManager.executeMock(serviceName, methodName, args, method.getReturnType());
            } else if (matchedRule.isTamper()) {
                // 篡改模式：先发起真实调用，再合并 Mock 数据
                return invokeWithTamper(matchedRule, serviceName, methodName, method, args);
            }
        }

        // 构建 RpcRequest
        RpcRequest request = buildRpcRequest(method, args);

        // 发送请求并等待响应
        return sendRequest(request, method);
    }

    /**
     * 篡改模式调用：先真实调用，再合并 Mock 数据
     */
    private Object invokeWithTamper(MockRule rule, String serviceName, String methodName,
                                    Method method, Object[] args) throws Throwable {
        // 构建 RpcRequest
        RpcRequest request = buildRpcRequest(method, args);

        // 发起真实网络请求
        Object realResponse = sendRequest(request, method);

        // 执行数据篡改
        return mockRuleManager.executeTamper(rule, serviceName, methodName, realResponse, method.getReturnType());
    }

    /**
     * 构建 RPC 请求对象
     *
     * @param method 方法
     * @param args   参数
     * @return RpcRequest
     */
    private RpcRequest buildRpcRequest(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(requestIdGenerator.nextId());
        request.setInterfaceName(interfaceClass.getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args != null ? args : new Object[0]);
        request.setVersion(version);
        return request;
    }

    /**
     * 发送 RPC 请求
     *
     * 完整流程：
     * 1. 服务发现 - 从本地缓存获取可用服务实例列表
     * 2. 负载均衡 - 使用 LoadBalancer 选择一个目标
     * 3. 连接管理 - 从连接池获取或创建 Channel
     * 4. 发送数据
     *
     * @param request RPC 请求
     * @param method  调用的方法（用于返回类型转换）
     * @return 方法返回值
     * @throws Exception 调用异常
     */
    private Object sendRequest(RpcRequest request, Method method) throws Exception {
        String serviceName = request.getInterfaceName();

        // ========== 步骤1: 服务发现 ==========
        List<ServiceInstance> instances = ServiceDiscovery.getServiceInstances(serviceName, version);

        if (instances.isEmpty()) {
            // 尝试获取所有版本的服务实例
            instances = ServiceDiscovery.getServiceInstances(serviceName);
            if (instances.isEmpty()) {
                logger.error("No available service provider for: {}", serviceName);
                throw new NoProviderAvailableException(serviceName);
            }
        }

        // ========== 步骤2: 负载均衡 ==========
        List<InetSocketAddress> addresses = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            addresses.add(new InetSocketAddress(instance.getHost(), instance.getPort()));
        }

        InetSocketAddress targetAddress = loadBalancer.select(addresses, serviceName);
        if (targetAddress == null) {
            logger.error("Load balancer returned no address for service: {}", serviceName);
            throw new NoProviderAvailableException(serviceName, "负载均衡器无法选择服务地址");
        }

        logger.debug("Selected target address: {}:{} for service: {}",
                targetAddress.getHostString(), targetAddress.getPort(), serviceName);

        // ========== 步骤3 & 4: 获取连接并发送 ==========
        // 构建 RpcMessage
        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(serializer.getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        // 创建 CompletableFuture 用于等待响应
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingRequestManager.addPendingRequest(request.getRequestId(), future);

        try {
            // 发送消息（通过连接池自动获取或创建连接）
            if (logger.isDebugEnabled()) {
                logger.debug("Sending RPC request: {} to {}", request, targetAddress);
            }
            nettyClient.sendMessage(targetAddress, message);

            // 等待响应
            RpcResponse response = future.get(timeout, TimeUnit.MILLISECONDS);

            // 处理响应
            if (response == null) {
                throw new RuntimeException("RPC response is null");
            }

            if (!response.isSuccess()) {
                throw new RuntimeException("RPC call failed: " + response.getMessage());
            }

            // 获取响应数据并进行类型转换兜底
            Object result = response.getData();
            result = convertResultType(result, method);

            return result;

        } finally {
            // 确保从待处理请求中移除
            pendingRequestManager.removePendingRequest(request.getRequestId());
        }
    }

    /**
     * 转换结果类型（兜底处理）
     *
     * 当 ObjectMapper 反序列化出的对象类型为 LinkedHashMap 时，
     * 尝试转换为方法声明的返回类型
     *
     * @param result 原始响应数据
     * @param method 调用的方法
     * @return 转换后的结果
     */
    private Object convertResultType(Object result, Method method) {
        if (result == null) {
            return null;
        }

        Class<?> returnType = method.getReturnType();

        // 如果类型已经匹配，直接返回
        if (returnType.isAssignableFrom(result.getClass())) {
            return result;
        }

        // 兜底转换：将 LinkedHashMap 转换为目标类型
        if (objectMapper != null && result instanceof java.util.Map) {
            try {
                Object converted = objectMapper.convertValue(result, returnType);
                logger.debug("Converted result from {} to {}", result.getClass().getName(), returnType.getName());
                return converted;
            } catch (Exception e) {
                logger.warn("Failed to convert result type: {} -> {}",
                        result.getClass().getName(), returnType.getName(), e);
                // 转换失败时返回原始结果，让调用方收到 ClassCastException
            }
        }

        return result;
    }
}
