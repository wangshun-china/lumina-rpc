package com.lumina.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.controlplane.entity.ServiceInstanceEntity;
import com.lumina.controlplane.repository.ServiceInstanceRepository;
import com.lumina.rpc.protocol.common.PendingRequestManager;
import com.lumina.rpc.protocol.common.RequestIdGenerator;
import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.spi.SerializerManager;
import com.lumina.rpc.protocol.transport.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 泛化调用服务
 *
 * 控制平面的 RPC 客户端，允许通过 HTTP 接口直接调用微服务
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
@Service
public class GenericInvokeService {

    private static final Logger logger = LoggerFactory.getLogger(GenericInvokeService.class);

    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ObjectMapper objectMapper;

    // Netty 客户端缓存（按服务名缓存）
    private final Map<String, NettyClient> clientCache = new ConcurrentHashMap<>();

    // 序列化器
    private final Serializer serializer;

    // 待处理请求管理器（使用单例，与 NettyClientHandler 共享）
    private final PendingRequestManager pendingRequestManager = PendingRequestManager.getInstance();

    // 请求 ID 生成器
    private final RequestIdGenerator requestIdGenerator = RequestIdGenerator.getInstance();

    public GenericInvokeService(ServiceInstanceRepository serviceInstanceRepository) {
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.objectMapper = new ObjectMapper();
        this.serializer = SerializerManager.getDefaultSerializer();
    }

    /**
     * 执行泛化调用
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param args        参数列表
     * @param timeout     超时时间（毫秒）
     * @return 调用结果
     */
    public Object invoke(String serviceName, String methodName, Object[] args, Long timeout) throws Exception {
        // 1. 查找可用的服务实例
        List<ServiceInstanceEntity> healthyInstances = serviceInstanceRepository.findHealthyInstances(LocalDateTime.now());

        ServiceInstanceEntity targetInstance = healthyInstances.stream()
                .filter(i -> i.getServiceName().equals(serviceName))
                .findFirst()
                .orElse(null);

        if (targetInstance == null) {
            throw new RuntimeException("No available instance for service: " + serviceName);
        }

        logger.info("🎯 选择目标实例: {} -> {}:{}", serviceName, targetInstance.getHost(), targetInstance.getPort());

        // 2. 获取服务元数据，用于参数类型转换
        Map<String, Object> metadata = getServiceMetadata(serviceName);
        Class<?>[] paramTypes = inferParameterTypes(args, metadata, methodName);

        // 参数个数校验
        if (args != null && paramTypes != null && args.length != paramTypes.length) {
            throw new RuntimeException("参数个数不匹配：前端传入 " + args.length + " 个参数，但方法 " + methodName + " 需要 " + paramTypes.length + " 个参数");
        }

        // 3. 将前端传来的参数转换为目标方法的真实类型
        Object[] convertedArgs = convertArgs(args, metadata, methodName, paramTypes);

        // 4. 获取或创建 Netty 客户端
        NettyClient client = getOrCreateClient(serviceName);

        // 5. 构建 RpcRequest
        RpcRequest request = new RpcRequest();
        request.setRequestId(requestIdGenerator.nextId());
        request.setInterfaceName(serviceName);
        request.setMethodName(methodName);
        request.setParameters(convertedArgs != null ? convertedArgs : new Object[0]);
        request.setParameterTypes(paramTypes);

        logger.info("📤 发送 RPC 请求: {}.{}({})", serviceName, methodName, convertedArgs != null ? convertedArgs.length : 0);

        // 4. 构建 RpcMessage
        RpcMessage message = new RpcMessage();
        message.setMagicNumber(RpcMessage.MAGIC_NUMBER);
        message.setVersion(RpcMessage.VERSION);
        message.setSerializerType(serializer.getType());
        message.setMessageType(RpcMessage.REQUEST);
        message.setRequestId(request.getRequestId());
        message.setBody(request);

        // 5. 发送请求并等待响应
        InetSocketAddress address = new InetSocketAddress(targetInstance.getHost(), targetInstance.getPort());

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        // 关键修复：使用 PendingRequestManager 单例注册请求
        pendingRequestManager.addPendingRequest(request.getRequestId(), future);

        try {
            // 发送消息
            client.sendMessage(address, message);

            // 等待响应
            long actualTimeout = timeout != null ? timeout : 5000L;
            logger.info("⏳ 等待响应 (timeout={}ms)...", actualTimeout);
            RpcResponse response = future.get(actualTimeout, TimeUnit.MILLISECONDS);

            if (response == null) {
                throw new RuntimeException("RPC response is null");
            }

            if (!response.isSuccess()) {
                throw new RuntimeException("RPC call failed: " + response.getMessage());
            }

            logger.info("✅ RPC 调用成功: {}.{}", serviceName, methodName);
            return response.getData();

        } finally {
            pendingRequestManager.removePendingRequest(request.getRequestId());
        }
    }

    /**
     * 推断参数类型
     *
     * 前端传来的参数可能是 ArrayList、LinkedHashMap 等 JSON 反序列化类型
     * 需要根据方法签名转换为正确的类型
     */
    private Class<?>[] inferParameterTypes(Object[] args, Map<String, Object> metadata, String methodName) {
        if (args == null || args.length == 0) {
            return new Class<?>[0];
        }

        Class<?>[] paramTypes = new Class<?>[args.length];

        // 尝试从元数据获取方法签名
        if (metadata != null && metadata.containsKey("methods")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) metadata.get("methods");
            for (Map<String, Object> method : methods) {
                if (methodName.equals(method.get("name"))) {
                    @SuppressWarnings("unchecked")
                    List<String> expectedTypes = (List<String>) method.get("parameterTypes");
                    if (expectedTypes != null && expectedTypes.size() == args.length) {
                        for (int i = 0; i < expectedTypes.size(); i++) {
                            paramTypes[i] = resolveType(expectedTypes.get(i), args[i]);
                        }
                        return paramTypes;
                    } else if (expectedTypes != null && expectedTypes.size() > 0) {
                        // 参数个数不完全匹配，但有元数据，回退到部分匹配
                        for (int i = 0; i < Math.min(expectedTypes.size(), args.length); i++) {
                            paramTypes[i] = resolveType(expectedTypes.get(i), args[i]);
                        }
                        // 剩余的参数使用推断类型
                        for (int i = expectedTypes.size(); i < args.length; i++) {
                            paramTypes[i] = inferTypeFromValue(args[i]);
                        }
                        return paramTypes;
                    }
                }
            }
        }

        // 回退：根据实际参数值推断
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = inferTypeFromValue(args[i]);
        }

        return paramTypes;
    }

    /**
     * 转换参数为目标方法的真实类型
     *
     * 使用 objectMapper.convertValue() 将前端传来的参数强制转换成目标方法所需的真实 Java 类型
     */
    private Object[] convertArgs(Object[] args, Map<String, Object> metadata, String methodName, Class<?>[] paramTypes) {
        if (args == null || args.length == 0) {
            return args;
        }

        if (paramTypes == null || paramTypes.length != args.length) {
            return args;
        }

        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                converted[i] = null;
                continue;
            }

            // 如果已经是目标类型，直接使用
            if (paramTypes[i].isAssignableFrom(args[i].getClass())) {
                converted[i] = args[i];
            } else {
                // 使用 convertValue 进行类型转换
                try {
                    converted[i] = objectMapper.convertValue(args[i], paramTypes[i]);
                    logger.debug("🔄 参数 {} 类型转换: {} -> {}", i, args[i].getClass().getSimpleName(), paramTypes[i].getSimpleName());
                } catch (Exception e) {
                    logger.warn("⚠️ 参数 {} 类型转换失败，使用原值: {}", i, e.getMessage());
                    converted[i] = args[i];
                }
            }
        }

        return converted;
    }

    /**
     * 根据类型名称和实际值解析参数类型
     */
    private Class<?> resolveType(String typeName, Object value) {
        if (typeName == null) {
            return inferTypeFromValue(value);
        }

        // 常见类型映射
        switch (typeName) {
            case "java.lang.String":
            case "String":
                return String.class;
            case "int":
            case "java.lang.Integer":
                return Integer.class;
            case "long":
            case "java.lang.Long":
                return Long.class;
            case "boolean":
            case "java.lang.Boolean":
                return Boolean.class;
            case "double":
            case "java.lang.Double":
                return Double.class;
            case "float":
            case "java.lang.Float":
                return Float.class;
            default:
                // 对于复杂类型，使用实际值的类型
                return inferTypeFromValue(value);
        }
    }

    /**
     * 从实际值推断类型
     */
    private Class<?> inferTypeFromValue(Object value) {
        if (value == null) {
            return Object.class;
        }

        // 处理 JSON 反序列化后的类型
        if (value instanceof String) {
            return String.class;
        } else if (value instanceof Integer) {
            return Integer.class;
        } else if (value instanceof Long) {
            return Long.class;
        } else if (value instanceof Double) {
            return Double.class;
        } else if (value instanceof Boolean) {
            return Boolean.class;
        } else if (value instanceof List) {
            return List.class;
        } else if (value instanceof Map) {
            return Map.class;
        }

        return value.getClass();
    }

    /**
     * 获取或创建 Netty 客户端
     */
    private NettyClient getOrCreateClient(String serviceName) {
        return clientCache.computeIfAbsent(serviceName, k -> {
            logger.info("🔧 创建 Netty 客户端: {}", serviceName);
            return new NettyClient(serializer);
        });
    }

    /**
     * 获取服务元数据
     *
     * @param serviceName 服务名称
     * @return 元数据 Map（直接返回 methods 列表）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServiceMetadata(String serviceName) {
        List<ServiceInstanceEntity> instances = serviceInstanceRepository.findByServiceName(serviceName);

        if (instances.isEmpty()) {
            return null;
        }

        // 从第一个实例获取元数据
        ServiceInstanceEntity instance = instances.get(0);
        String metadataJson = instance.getServiceMetadata();

        if (metadataJson == null || metadataJson.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> rawMetadata = objectMapper.readValue(metadataJson, Map.class);

            // 数据库中的格式是 {"services": [{"methods": [...]}]}
            // 需要展平为 {"methods": [...]} 方便前端使用
            if (rawMetadata.containsKey("services")) {
                List<Map<String, Object>> services = (List<Map<String, Object>>) rawMetadata.get("services");
                if (services != null && !services.isEmpty()) {
                    Map<String, Object> firstService = services.get(0);
                    if (firstService.containsKey("methods")) {
                        // 直接返回 methods 列表
                        Map<String, Object> result = new HashMap<>();
                        result.put("interfaceName", firstService.get("interfaceName"));
                        result.put("methods", firstService.get("methods"));
                        return result;
                    }
                }
            }

            // 如果已经是展平格式，直接返回
            return rawMetadata;
        } catch (Exception e) {
            logger.warn("解析服务元数据失败: {}", serviceName, e);
            return null;
        }
    }

    /**
     * 获取所有服务的元数据
     *
     * @return 服务元数据列表
     */
    public List<Map<String, Object>> getAllServiceMetadata() {
        List<ServiceInstanceEntity> instances = serviceInstanceRepository.findHealthyInstances(LocalDateTime.now());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ServiceInstanceEntity instance : instances) {
            if (instance.getServiceMetadata() != null && !instance.getServiceMetadata().isEmpty()) {
                try {
                    Map<String, Object> metadata = objectMapper.readValue(instance.getServiceMetadata(), Map.class);
                    metadata.put("instanceId", instance.getInstanceId());
                    metadata.put("host", instance.getHost());
                    metadata.put("port", instance.getPort());
                    result.add(metadata);
                } catch (Exception e) {
                    logger.warn("解析服务元数据失败: {}", instance.getServiceName(), e);
                }
            }
        }

        return result;
    }

    /**
     * 关闭所有客户端
     */
    public void shutdown() {
        for (NettyClient client : clientCache.values()) {
            try {
                client.shutdown();
            } catch (Exception e) {
                logger.warn("关闭 Netty 客户端失败", e);
            }
        }
        clientCache.clear();
        logger.info("所有泛化调用客户端已关闭");
    }
}