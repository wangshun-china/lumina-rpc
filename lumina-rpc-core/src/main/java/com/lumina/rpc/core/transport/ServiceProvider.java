package com.lumina.rpc.core.transport;

import com.lumina.rpc.core.annotation.LuminaService;
import com.lumina.rpc.core.discovery.ServiceRegistryClient;
import com.lumina.rpc.core.metadata.ServiceMetadataExtractor;
import com.lumina.rpc.core.shutdown.GracefulShutdownManager;
import com.lumina.rpc.core.shutdown.ShutdownConfigClient;
import com.lumina.rpc.protocol.spi.Serializer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务提供者
 *
 * 封装了服务注册和服务器启动的功能
 *
 * 防御性编程特性：
 * 1. 优雅停机：实现 @PreDestroy，确保 Spring 关闭时正确释放资源
 * 2. 先从控制平面注销，再关闭 Netty 服务器
 * 3. 防止重复关闭
 *
 * 企业级特性：
 * 4. 服务元数据自动上报：提取接口方法信息并上报到控制平面
 */
public class ServiceProvider {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProvider.class);

    // Netty 服务器
    private NettyServer nettyServer;

    // 服务注册表
    private ServiceRegistry serviceRegistry;

    // 序列化器
    private Serializer serializer;

    // 端口
    private int port;

    // 服务主机地址
    private String host;

    // 是否已注册到控制平面
    private boolean registeredToControlPlane = false;

    // 关闭标志
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // 已发布的服务接口列表（用于元数据提取）
    private final List<Class<?>> publishedInterfaces = new ArrayList<>();

    public ServiceProvider(int port, Serializer serializer) {
        this.port = port;
        this.serializer = serializer;
        this.serviceRegistry = new ServiceRegistry();
        // 创建请求处理器
        DefaultRpcRequestHandler requestHandler = new DefaultRpcRequestHandler(serviceRegistry);
        this.nettyServer = new NettyServer(serializer, requestHandler);
    }

    public ServiceProvider() {
        this.port = 0;
        this.serializer = null;
        this.serviceRegistry = new ServiceRegistry();
        this.nettyServer = null;
    }

    /**
     * 初始化服务提供者（用于无参构造后设置参数）
     *
     * @param port       端口号
     * @param serializer 序列化器
     */
    public void init(int port, Serializer serializer) {
        init(port, serializer, "127.0.0.1");
    }

    /**
     * 初始化服务提供者（用于无参构造后设置参数）
     *
     * @param port       端口号
     * @param serializer 序列化器
     * @param host       主机地址
     */
    public void init(int port, Serializer serializer, String host) {
        this.port = port;
        this.serializer = serializer;
        this.host = host;
        DefaultRpcRequestHandler requestHandler = new DefaultRpcRequestHandler(serviceRegistry);
        this.nettyServer = new NettyServer(serializer, requestHandler);
    }

    /**
     * 设置服务主机地址
     *
     * @param host 主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 设置控制平面 URL
     *
     * @param controlPlaneUrl 控制平面 URL
     */
    public void setControlPlaneUrl(String controlPlaneUrl) {
        ServiceRegistryClient.setControlPlaneUrl(controlPlaneUrl);
    }

    /**
     * 发布服务
     *
     * @param serviceBean 服务实现实例
     */
    public void publishService(Object serviceBean) {
        // 穿透 Spring CGLIB 代理，获取真实的业务接口
        Class<?> targetClass = unwrapProxy(serviceBean.getClass());

        // 获取服务类上的 @LuminaService 注解
        LuminaService serviceAnnotation = targetClass.getAnnotation(LuminaService.class);

        Class<?> interfaceClass;
        String version;

        if (serviceAnnotation != null) {
            // 从注解获取配置
            interfaceClass = serviceAnnotation.interfaceClass();
            if (interfaceClass == void.class) {
                // 如果没有指定接口，穿透代理获取真实的业务接口
                interfaceClass = resolveBusinessInterface(serviceBean);
                if (interfaceClass == null) {
                    throw new IllegalArgumentException("Service must implement an interface: " +
                            targetClass.getName());
                }
            }
            version = serviceAnnotation.version();
        } else {
            // 没有注解，自动推断（穿透代理）
            interfaceClass = resolveBusinessInterface(serviceBean);
            if (interfaceClass == null) {
                throw new IllegalArgumentException("Service must implement an interface: " +
                        targetClass.getName());
            }
            version = "";
        }

        // 注册服务
        String interfaceName = interfaceClass.getName();
        serviceRegistry.registerService(interfaceName, version, serviceBean);

        // 记录已发布的接口（用于元数据提取）
        if (!publishedInterfaces.contains(interfaceClass)) {
            publishedInterfaces.add(interfaceClass);
        }

        logger.info("Published service: interface={}, version={}, implementation={}",
                interfaceName, version, targetClass.getName());
    }

    /**
     * 穿透 Spring CGLIB 代理，获取真实的目标类
     *
     * @param clazz 可能是代理类的 Class
     * @return 真实的目标类
     */
    private Class<?> unwrapProxy(Class<?> clazz) {
        Class<?> current = clazz;
        // CGLIB 代理类名包含 $$，循环向上查找真实类
        while (current != null && current.getName().contains("$$")) {
            current = current.getSuperclass();
        }
        return current != null ? current : clazz;
    }

    /**
     * 从 Bean 实例中解析真实的业务接口
     *
     * 必须穿透 Spring 代理，并排除 Spring 内部接口：
     * - org.springframework.aop.SpringProxy
     * - org.springframework.aop.framework.Advised
     * - org.springframework.cglib.proxy.Decorator
     * - java.lang 内置接口
     *
     * @param serviceBean 服务 Bean 实例
     * @return 真实的业务接口 Class，找不到则返回 null
     */
    private Class<?> resolveBusinessInterface(Object serviceBean) {
        Class<?> beanClass = serviceBean.getClass();
        Class<?> targetClass = unwrapProxy(beanClass);

        // 需要排除的 Spring 内部接口前缀
        Set<String> excludedPrefixes = Set.of(
                "org.springframework.aop.",
                "org.springframework.cglib.",
                "java.",
                "jakarta."
        );

        // 1. 先检查目标类直接实现的接口
        for (Class<?> iface : targetClass.getInterfaces()) {
            if (!isExcludedInterface(iface, excludedPrefixes)) {
                logger.debug("Resolved business interface from target class: {}", iface.getName());
                return iface;
            }
        }

        // 2. 检查代理类实现的接口（可能是 JDK 动态代理）
        for (Class<?> iface : beanClass.getInterfaces()) {
            if (!isExcludedInterface(iface, excludedPrefixes)) {
                logger.debug("Resolved business interface from proxy interfaces: {}", iface.getName());
                return iface;
            }
        }

        // 3. 递归检查父类
        Class<?> superClass = targetClass.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            for (Class<?> iface : superClass.getInterfaces()) {
                if (!isExcludedInterface(iface, excludedPrefixes)) {
                    logger.debug("Resolved business interface from superclass: {}", iface.getName());
                    return iface;
                }
            }
            superClass = superClass.getSuperclass();
        }

        return null;
    }

    /**
     * 判断接口是否为需要排除的框架内部接口
     */
    private boolean isExcludedInterface(Class<?> iface, Set<String> excludedPrefixes) {
        String ifaceName = iface.getName();
        for (String prefix : excludedPrefixes) {
            if (ifaceName.startsWith(prefix)) {
                return true;
            }
        }
        // 额外检查特定的 Spring 标记接口
        return SpringProxy.class.isAssignableFrom(iface) ||
               Advised.class.isAssignableFrom(iface);
    }

    /**
     * 注册服务（手动指定接口和版本）
     *
     * @param interfaceClass 接口类
     * @param version        版本号
     * @param serviceBean    服务实例
     */
    public void registerService(Class<?> interfaceClass, String version, Object serviceBean) {
        serviceRegistry.registerService(interfaceClass.getName(), version, serviceBean);

        // 记录已发布的接口
        if (!publishedInterfaces.contains(interfaceClass)) {
            publishedInterfaces.add(interfaceClass);
        }

        logger.info("Registered service: interface={}, version={}, implementation={}",
                interfaceClass.getName(), version, serviceBean.getClass().getName());
    }

    /**
     * 启动服务器
     */
    public void start() {
        if (nettyServer == null) {
            throw new IllegalStateException("ServiceProvider not initialized. Call init() first or use constructor with parameters.");
        }

        // 确定服务名
        String primaryServiceName = serviceRegistry.getAllServiceNames().stream().findFirst().orElse("unknown");

        // ========== 注册优雅停机回调 ==========
        GracefulShutdownManager shutdownManager = GracefulShutdownManager.getInstance();
        shutdownManager.setShutdownCallback(() -> {
            // 从控制平面注销
            if (registeredToControlPlane) {
                try {
                    ServiceRegistryClient.shutdown();
                    logger.info("📡 [Graceful Shutdown] Deregistered from Control Plane");
                } catch (Exception e) {
                    logger.warn("Error during Control Plane deregistration", e);
                }
            }
        });
        // 设置停机超时时间（10秒）
        shutdownManager.setShutdownTimeout(10000);

        // ========== 启动停机配置同步客户端 ==========
        ShutdownConfigClient configClient = ShutdownConfigClient.getInstance();
        configClient.start(primaryServiceName);

        // 在新线程中启动服务器，避免阻塞
        new Thread(() -> nettyServer.start(port), "rpc-server-starter").start();

        // 向控制平面注册服务实例
        registerToControlPlane();
    }

    /**
     * 向控制平面注册服务实例
     */
    private void registerToControlPlane() {
        if (registeredToControlPlane) {
            return;
        }

        // 获取本地注册的服务列表
        var serviceNames = serviceRegistry.getAllServiceNames();
        if (serviceNames.isEmpty()) {
            logger.warn("No services registered locally, skipping control plane registration");
            return;
        }

        // 使用第一个服务作为服务名（简化处理）
        String primaryServiceName = serviceNames.stream().findFirst().orElse(null);
        if (primaryServiceName != null) {
            // 确定主机地址
            String registryHost = (host != null && !host.isEmpty()) ? host : "127.0.0.1";

            // 提取服务元数据
            String metadataJson = null;
            if (!publishedInterfaces.isEmpty()) {
                metadataJson = ServiceMetadataExtractor.extractMetadataBatch(publishedInterfaces);
                logger.info("📋 [Metadata Extraction] Extracted metadata for {} interfaces", publishedInterfaces.size());

                // ==================== 高亮日志：打印完整的元数据 JSON ====================
                if (metadataJson != null) {
                    logger.info("📤 [Metadata JSON] Full service metadata payload:\n{}", metadataJson);
                } else {
                    logger.warn("⚠️ [Metadata Extraction] Metadata JSON is null!");
                }
            } else {
                logger.warn("⚠️ [Metadata Extraction] No published interfaces found, metadata will not be sent!");
            }

            // 注册到控制平面（带元数据）
            try {
                ServiceRegistryClient.init(primaryServiceName, registryHost, port, "", metadataJson);
                registeredToControlPlane = true;

                logger.info("✅ [Control Plane] Registered to control plane: {} at {}:{}",
                        primaryServiceName, registryHost, port);
            } catch (Exception e) {
                // 注册失败不应阻断服务启动
                logger.warn("⚠️ [Control Plane] Failed to register to control plane, service will continue: {}",
                        e.getMessage());
            }
        }
    }

    /**
     * 关闭服务提供者
     *
     * 优雅停机流程（对标 Dubbo）：
     * 1. 触发 GracefulShutdownManager 开始停机
     * 2. 从控制平面注销（通知消费者不再路由）
     * 3. 标记停机状态，拒绝新请求
     * 4. 等待正在处理的请求完成（in-flight requests）
     * 5. 关闭 Netty 服务器
     * 6. 清理本地服务注册表
     */
    @PreDestroy
    public void shutdown() {
        // 防止重复关闭
        if (!shutdown.compareAndSet(false, true)) {
            logger.info("ServiceProvider already shut down");
            return;
        }

        logger.info("🛑 [Graceful Shutdown] Initiating graceful shutdown...");

        // 1. 触发优雅停机（会等待 in-flight 请求完成）
        GracefulShutdownManager.getInstance().gracefulShutdown();

        // 2. 关闭 Netty 服务器
        if (nettyServer != null) {
            try {
                nettyServer.shutdown();
                logger.info("⚡ [Graceful Shutdown] Netty Server stopped");
            } catch (Exception e) {
                logger.warn("Error during Netty Server shutdown", e);
            }
        }

        // 3. 清理本地服务注册表
        if (serviceRegistry != null) {
            serviceRegistry.clear();
            logger.info("🗑️ [Graceful Shutdown] Service registry cleared");
        }

        logger.info("✅ [Graceful Shutdown] ServiceProvider shutdown complete");
    }

    /**
     * 获取端口号
     *
     * @return 端口号
     */
    public int getPort() {
        return port;
    }

    /**
     * 获取服务注册表
     *
     * @return 服务注册表
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /**
     * 获取已发布的服务接口列表
     *
     * @return 接口列表
     */
    public List<Class<?>> getPublishedInterfaces() {
        return new ArrayList<>(publishedInterfaces);
    }
}
