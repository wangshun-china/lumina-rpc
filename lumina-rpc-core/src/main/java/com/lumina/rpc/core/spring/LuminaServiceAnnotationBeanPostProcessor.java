package com.lumina.rpc.core.spring;

import com.lumina.rpc.core.annotation.LuminaService;
import com.lumina.rpc.core.transport.ServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LuminaService 注解处理器
 *
 * 采用"缓存 + 事件延迟暴露"机制，避免 Spring Bean 过早初始化问题。
 *
 * 生命周期：
 * 1. postProcessAfterInitialization: 仅缓存带有 @LuminaService 的 Bean，不做任何注册
 * 2. onApplicationEvent(ContextRefreshedEvent): 所有 Bean 安全初始化后，批量注册服务并启动 Netty
 *
 * 这是所有开源中间件整合 Spring Boot 的标准做法。
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(200)
public class LuminaServiceAnnotationBeanPostProcessor
        implements BeanPostProcessor, ApplicationContextAware,
        ApplicationListener<ContextRefreshedEvent> {

    private ApplicationContext applicationContext;

    /**
     * 缓存待注册的服务 Bean
     * Key: beanName, Value: ServiceBeanInfo
     */
    private final Map<String, ServiceBeanInfo> cachedServiceBeans = new ConcurrentHashMap<>();

    /**
     * 标记是否已完成服务暴露（防止重复执行）
     */
    private volatile boolean exported = false;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 检查类是否有 @LuminaService 注解
        Class<?> targetClass = getTargetClass(bean.getClass());

        LuminaService luminaService = findLuminaServiceAnnotation(targetClass);

        if (luminaService != null) {
            // ========== 核心改动：只缓存，不注册！ ==========
            // 此时不能调用 applicationContext.getBean(ServiceProvider.class)
            // 因为可能 ServiceProvider 还未完全初始化，会导致 Bean 过早初始化问题
            ServiceBeanInfo info = new ServiceBeanInfo(bean, targetClass, luminaService, beanName);
            cachedServiceBeans.put(beanName, info);

            log.debug("📦 [Lumina-RPC] Cached @LuminaService bean: {} (will be registered on ContextRefreshedEvent)",
                    targetClass.getSimpleName());
        }

        return bean;
    }

    /**
     * 当所有 Spring Bean 初始化完成后，批量注册服务
     *
     * ContextRefreshedEvent 表示：
     * - 所有 Bean 定义已加载
     * - 所有 Bean 已完成实例化和属性注入
     * - 所有 BeanPostProcessor 已执行完毕
     *
     * 此时安全获取 ServiceProvider 并进行服务注册和启动！
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 防止重复执行（父子 Context 可能触发多次）
        if (exported) {
            return;
        }
        exported = true;

        if (cachedServiceBeans.isEmpty()) {
            log.info("ℹ️ [Lumina-RPC] No @LuminaService beans found, skipping service export");
            return;
        }

        log.info("🚀 [Lumina-RPC] ContextRefreshedEvent received, starting service export for {} beans...",
                cachedServiceBeans.size());

        try {
            // 安全获取 ServiceProvider（此时所有 Bean 已完全初始化）
            ServiceProvider serviceProvider = applicationContext.getBean(ServiceProvider.class);

            // 批量注册所有缓存的服务
            for (ServiceBeanInfo info : cachedServiceBeans.values()) {
                registerService(serviceProvider, info);
            }

            // 启动 Netty Server 并向控制平面注册
            serviceProvider.start();

            log.info("✅ [Lumina-RPC] Service export complete, {} services registered on port {}",
                    cachedServiceBeans.size(), serviceProvider.getPort());

        } catch (Exception e) {
            log.error("❌ [Lumina-RPC] Failed to export services", e);
            throw new RuntimeException("Failed to export Lumina services", e);
        }
    }

    /**
     * 注册单个服务
     */
    private void registerService(ServiceProvider serviceProvider, ServiceBeanInfo info) {
        try {
            Class<?> interfaceClass = resolveInterfaceClass(info.targetClass, info.luminaService);

            if (interfaceClass == null) {
                log.warn("⚠️ [Lumina-RPC] No interface found for service: {} (bean: {})",
                        info.targetClass.getName(), info.beanName);
                return;
            }

            // 注册服务到 ServiceProvider
            serviceProvider.publishService(info.bean);

            log.info("✅ [Lumina-RPC] Registered @LuminaService: {} -> {} (bean: {})",
                    info.targetClass.getSimpleName(), interfaceClass.getName(), info.beanName);

        } catch (Exception e) {
            log.error("❌ [Lumina-RPC] Failed to register @LuminaService for bean: {}", info.beanName, e);
        }
    }

    /**
     * 解析服务接口类
     */
    private Class<?> resolveInterfaceClass(Class<?> targetClass, LuminaService luminaService) {
        // 优先使用注解指定的接口
        if (luminaService.interfaceClass() != void.class) {
            return luminaService.interfaceClass();
        }

        // 从实现的接口中查找
        Class<?>[] interfaces = targetClass.getInterfaces();
        if (interfaces.length == 0) {
            return null;
        }

        // 优先找到带 @LuminaService 的接口，或第一个非标记接口
        for (Class<?> iface : interfaces) {
            if (iface.isAnnotationPresent(LuminaService.class) ||
                (!iface.getName().startsWith("org.springframework") &&
                 !iface.getName().startsWith("java."))) {
                return iface;
            }
        }

        return interfaces[0];
    }

    /**
     * 查找 @LuminaService 注解（支持类上和接口上）
     */
    private LuminaService findLuminaServiceAnnotation(Class<?> targetClass) {
        // 检查类上的注解
        LuminaService luminaService = targetClass.getAnnotation(LuminaService.class);
        if (luminaService != null) {
            return luminaService;
        }

        // 检查接口上的注解
        for (Class<?> iface : targetClass.getInterfaces()) {
            if (iface.isAnnotationPresent(LuminaService.class)) {
                return iface.getAnnotation(LuminaService.class);
            }
        }

        return null;
    }

    /**
     * 获取目标类（处理 CGLIB 代理）
     */
    private Class<?> getTargetClass(Class<?> clazz) {
        if (clazz.getName().contains("$$")) {
            return clazz.getSuperclass();
        }
        return clazz;
    }

    /**
     * 服务 Bean 信息缓存结构
     */
    private static class ServiceBeanInfo {
        final Object bean;
        final Class<?> targetClass;
        final LuminaService luminaService;
        final String beanName;

        ServiceBeanInfo(Object bean, Class<?> targetClass, LuminaService luminaService, String beanName) {
            this.bean = bean;
            this.targetClass = targetClass;
            this.luminaService = luminaService;
            this.beanName = beanName;
        }
    }
}