package com.lumina.rpc.core.proxy;

import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.transport.NettyClient;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态代理工厂
 *
 * 使用 ByteBuddy 创建 RPC 接口的动态代理
 */
public class ProxyFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProxyFactory.class);

    // Netty 客户端
    private NettyClient nettyClient;

    // 序列化器
    private Serializer serializer;

    // 默认超时时间
    private final long defaultTimeout;

    public ProxyFactory(NettyClient nettyClient, Serializer serializer, long defaultTimeout) {
        this.nettyClient = nettyClient;
        this.serializer = serializer;
        this.defaultTimeout = defaultTimeout;
    }

    public ProxyFactory(NettyClient nettyClient, Serializer serializer) {
        this(nettyClient, serializer, 5000);
    }

    /**
     * 无参构造函数 - 仅用于 Spring 代理或 SPI 场景
     * 注意：使用此构造函数后必须通过 setter 方法设置依赖
     */
    public ProxyFactory() {
        this.nettyClient = null;
        this.serializer = null;
        this.defaultTimeout = 5000;
    }

    /**
     * 设置 NettyClient (用于无参构造后的依赖注入)
     */
    public void setNettyClient(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    /**
     * 设置 Serializer (用于无参构造后的依赖注入)
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * 创建接口的动态代理
     *
     * @param interfaceClass 接口类
     * @param version        服务版本
     * @param timeout        超时时间
     * @param <T>            接口类型
     * @return 代理实例
     */
    public <T> T createProxy(Class<T> interfaceClass, String version, long timeout) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Class must be an interface: " + interfaceClass.getName());
        }

        try {
            // 创建 RpcClientHandler
            RpcClientHandler clientHandler = new RpcClientHandler(
                    interfaceClass,
                    version,
                    timeout,
                    serializer,
                    nettyClient
            );

            // 使用 ByteBuddy 创建代理
            @SuppressWarnings("unchecked")
            Class<T> proxyClass = (Class<T>) new ByteBuddy()
                    .subclass(Object.class)
                    .implement(interfaceClass)
                    .method(ElementMatchers.isDeclaredBy(interfaceClass))
                    .intercept(MethodDelegation.to(new ByteBuddyInterceptor(clientHandler)))
                    .make()
                    .load(interfaceClass.getClassLoader())
                    .getLoaded();

            T proxyInstance = proxyClass.getDeclaredConstructor().newInstance();

            if (logger.isDebugEnabled()) {
                logger.debug("Created proxy for interface: {}", interfaceClass.getName());
            }

            return proxyInstance;

        } catch (Exception e) {
            logger.error("Failed to create proxy for interface: {}", interfaceClass.getName(), e);
            throw new RuntimeException("Failed to create proxy for: " + interfaceClass.getName(), e);
        }
    }

    /**
     * 创建接口的动态代理（使用默认超时时间）
     *
     * @param interfaceClass 接口类
     * @param version        服务版本
     * @param <T>            接口类型
     * @return 代理实例
     */
    public <T> T createProxy(Class<T> interfaceClass, String version) {
        return createProxy(interfaceClass, version, defaultTimeout);
    }

    /**
     * 创建接口的动态代理（使用空版本号）
     *
     * @param interfaceClass 接口类
     * @param <T>            接口类型
     * @return 代理实例
     */
    public <T> T createProxy(Class<T> interfaceClass) {
        return createProxy(interfaceClass, "", defaultTimeout);
    }
}
