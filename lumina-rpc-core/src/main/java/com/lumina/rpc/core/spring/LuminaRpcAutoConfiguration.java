package com.lumina.rpc.core.spring;

import com.lumina.rpc.core.discovery.ServiceDiscoveryClient;
import com.lumina.rpc.core.mock.MockRuleSubscriptionClient;
import com.lumina.rpc.core.protection.ProtectionConfigClient;
import com.lumina.rpc.core.proxy.ProxyFactory;
import com.lumina.rpc.core.stats.RequestStatsReporter;
import com.lumina.rpc.protocol.pool.ChannelPoolManager;
import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.spi.SerializerManager;
import com.lumina.rpc.protocol.transport.NettyClient;
import com.lumina.rpc.core.transport.ServiceProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lumina-RPC 自动配置类
 *
 * 这是 Lumina-RPC 与 Spring Boot 整合的入口点，仅作为 Bean 工厂。
 * 当应用引入 lumina-rpc-core 依赖后，此配置类会自动生效。
 *
 * 核心功能：
 * 1. 注册 Serializer - 序列化器实例
 * 2. 注册 NettyClient - RPC 客户端（自带 @PreDestroy 优雅停机）
 * 3. 注册 ProxyFactory - 用于创建 RPC 客户端动态代理
 * 4. 注册 ServiceProvider - 服务提供者（自带 @PreDestroy 优雅停机）
 * 5. 启动服务发现客户端 - 从控制平面拉取服务实例
 * 6. 注册 BeanPostProcessor - 处理 @LuminaReference 和 @LuminaService 注解
 * 7. 启动 Mock 规则订阅 - 通过 SSE 接收动态降级规则
 *
 * 生命周期说明：
 * - 优雅停机逻辑由各个 Bean 自身的 @PreDestroy 方法处理
 * - 服务暴露时机由 LuminaServiceAnnotationBeanPostProcessor 通过 ContextRefreshedEvent 控制
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.lumina.rpc.core.spring")
public class LuminaRpcAutoConfiguration {

    /**
     * 控制平面地址
     */
    @Value("${lumina.rpc.control-plane-url:http://localhost:8080}")
    private String controlPlaneUrl;

    /**
     * 服务发现刷新间隔（秒）
     */
    @Value("${lumina.rpc.discovery-refresh-interval:30}")
    private int discoveryRefreshInterval;

    /**
     * RPC 服务端主机地址
     * 优先使用环境变量 LUMINA_RPC_SERVER_HOST（Docker 环境），
     * 其次使用配置文件 lumina.rpc.server.host，默认 127.0.0.1
     */
    @Value("${lumina.rpc.server.host:${LUMINA_RPC_SERVER_HOST:127.0.0.1}}")
    private String serverHost;

    /**
     * RPC 服务端口
     */
    @Value("${lumina.rpc.server.port:9000}")
    private int serverPort;

    /**
     * Mock 规则功能开关（默认关闭）
     * 开启后会订阅 Control Plane 的 Mock 规则变更，占用少量资源
     */
    @Value("${lumina.rpc.mock.enabled:false}")
    private boolean mockEnabled = false;

    /**
     * 需要订阅 Mock 规则的服务列表（逗号分隔）
     * 例如: com.lumina.sample.radar.service.RadarService,com.lumina.sample.engine.service.EngineService
     */
    @Value("${lumina.rpc.mock.subscribe-services:}")
    private String mockSubscribeServices;

    /**
     * 保护配置刷新间隔（秒）
     */
    @Value("${lumina.rpc.protection.refresh-interval:1}")
    private int protectionRefreshInterval = 1;

    /**
     * 构造方法 - 记录自动配置启动信息
     */
    public LuminaRpcAutoConfiguration() {
        log.info("🚀 [Lumina-RPC] Auto-Configuration initializing...");
    }

    /**
     * 初始化服务发现客户端
     */
    @PostConstruct
    public void initServiceDiscovery() {
        log.info("🔍 [Lumina-RPC] Initializing service discovery client, control plane: {}", controlPlaneUrl);
        ServiceDiscoveryClient.init(controlPlaneUrl, discoveryRefreshInterval);

        // 初始化保护配置客户端（熔断器/限流器动态配置）
        initProtectionConfigClient();

        // 初始化 Mock 规则订阅（如果启用）
        if (mockEnabled) {
            initMockSubscription();
        }
    }

    /**
     * 初始化保护配置客户端
     */
    private void initProtectionConfigClient() {
        log.info("🛡️ [Lumina-RPC] Initializing protection config client (refresh interval: {}s)", protectionRefreshInterval);
        ProtectionConfigClient.initialize(controlPlaneUrl, protectionRefreshInterval);
        ProtectionConfigClient.getInstance().startRefresh();

        // 初始化请求统计上报器
        RequestStatsReporter.initialize(controlPlaneUrl);
        RequestStatsReporter.getInstance().start();
    }

    /**
     * 初始化 Mock 规则订阅
     */
    private void initMockSubscription() {
        List<String> services = parseServiceList(mockSubscribeServices);
        if (!services.isEmpty()) {
            log.info("📡 [Lumina-RPC] Initializing Mock rule subscription for {} services", services.size());
            MockRuleSubscriptionClient.init(controlPlaneUrl, services);
        } else {
            log.info("📡 [Lumina-RPC] Mock enabled but no services configured for subscription");
        }
    }

    /**
     * 解析服务列表配置
     */
    private List<String> parseServiceList(String config) {
        if (config == null || config.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 注册 Serializer Bean
     *
     * 通过 SPI 加载默认的序列化器实现
     *
     * @return Serializer 实例
     */
    @Bean
    @ConditionalOnMissingBean(Serializer.class)
    public Serializer serializer() {
        Serializer serializer = SerializerManager.getDefaultSerializer();
        log.info("🔧 [Lumina-RPC] Registering Serializer Bean: {} (type={})",
                serializer.getName(), serializer.getType());
        return serializer;
    }

    /**
     * 注册 NettyClient Bean
     *
     * RPC 客户端，用于发起远程调用。
     * 优雅停机由 NettyClient 自身的 @PreDestroy 方法处理。
     *
     * @param serializer 序列化器实例
     * @return NettyClient 实例
     */
    @Bean
    @ConditionalOnMissingBean(NettyClient.class)
    public NettyClient nettyClient(Serializer serializer) {
        log.info("🔧 [Lumina-RPC] Registering NettyClient Bean for RPC client connections");

        NettyClient client = new NettyClient(serializer);

        // 初始化连接池管理器，绑定 ChannelFactory
        ChannelPoolManager poolManager = ChannelPoolManager.getInstance();
        poolManager.setChannelFactory(client);
        log.info("🔌 [Lumina-RPC] Connection pool initialized (min=2, max=10 per address)");

        return client;
    }

    /**
     * 注册 ProxyFactory Bean
     *
     * 这是 Consumer 端的核心组件，用于创建 RPC 接口的动态代理。
     * 当 @LuminaReference 注解被发现时，会使用此工厂生成代理实例。
     *
     * @param nettyClient NettyClient 实例
     * @param serializer Serializer 实例
     * @return ProxyFactory 实例
     */
    @Bean
    @ConditionalOnMissingBean(ProxyFactory.class)
    public ProxyFactory proxyFactory(NettyClient nettyClient, Serializer serializer) {
        log.info("🔧 [Lumina-RPC] Registering ProxyFactory Bean for RPC client proxy creation");
        return new ProxyFactory(nettyClient, serializer);
    }

    /**
     * 注册 ServiceProvider Bean
     *
     * 这是 Provider 端的核心组件，用于管理本地服务实例的注册和查找。
     * 优雅停机由 ServiceProvider 自身的 @PreDestroy 方法处理。
     *
     * @param serializer Serializer 实例
     * @return ServiceProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean(ServiceProvider.class)
    public ServiceProvider serviceProvider(Serializer serializer) {
        log.info("🔧 [Lumina-RPC] Registering ServiceProvider Bean for RPC service registration (port={})", serverPort);
        ServiceProvider provider = new ServiceProvider();
        provider.init(serverPort, serializer, serverHost);
        provider.setControlPlaneUrl(controlPlaneUrl);
        return provider;
    }

    /**
     * 注册 LuminaReference 注解处理器
     *
     * 这个 BeanPostProcessor 负责扫描 @LuminaReference 注解，
     * 并为标记的字段注入 RPC 客户端代理。
     *
     * @param proxyFactory ProxyFactory 实例
     * @return LuminaReferenceAnnotationBeanPostProcessor 实例
     */
    @Bean
    @ConditionalOnMissingBean(LuminaReferenceAnnotationBeanPostProcessor.class)
    public LuminaReferenceAnnotationBeanPostProcessor luminaReferenceAnnotationBeanPostProcessor(ProxyFactory proxyFactory) {
        log.info("🔧 [Lumina-RPC] Registering @LuminaReference Annotation Processor");
        return new LuminaReferenceAnnotationBeanPostProcessor(proxyFactory);
    }

    /**
     * 注册 LuminaService 注解处理器
     *
     * 这个 BeanPostProcessor 负责扫描 @LuminaService 注解。
     * 采用"缓存 + 事件延迟暴露"机制，在 ContextRefreshedEvent 时才进行服务注册和启动。
     *
     * @return LuminaServiceAnnotationBeanPostProcessor 实例
     */
    @Bean
    @ConditionalOnMissingBean(LuminaServiceAnnotationBeanPostProcessor.class)
    public LuminaServiceAnnotationBeanPostProcessor luminaServiceAnnotationBeanPostProcessor() {
        log.info("🔧 [Lumina-RPC] Registering @LuminaService Annotation Processor");
        return new LuminaServiceAnnotationBeanPostProcessor();
    }
}