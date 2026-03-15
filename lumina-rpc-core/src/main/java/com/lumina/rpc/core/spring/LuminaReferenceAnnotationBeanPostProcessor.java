package com.lumina.rpc.core.spring;

import com.lumina.rpc.core.annotation.LuminaReference;
import com.lumina.rpc.core.proxy.ProxyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * LuminaReference 注解处理器
 * 负责扫描并注入 @LuminaReference 标记的 RPC 客户端代理
 *
 * 这是 Lumina-RPC 与 Spring 整合的核心组件之一
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(100) // 确保在普通 BeanPostProcessor 之后执行
public class LuminaReferenceAnnotationBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private ProxyFactory proxyFactory;

    public LuminaReferenceAnnotationBeanPostProcessor() {
    }

    public LuminaReferenceAnnotationBeanPostProcessor(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 在 Bean 初始化之前处理字段注入
        Class<?> clazz = bean.getClass();

        // 处理当前类及其所有父类的字段
        while (clazz != null && clazz != Object.class) {
            processFields(bean, clazz);
            clazz = clazz.getSuperclass();
        }

        return bean;
    }

    /**
     * 处理指定类的字段
     */
    private void processFields(Object bean, Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            LuminaReference reference = field.getAnnotation(LuminaReference.class);
            if (reference != null) {
                injectLuminaReference(bean, field, reference);
            }
        }
    }

    /**
     * 注入 LuminaReference 代理
     */
    private void injectLuminaReference(Object bean, Field field, LuminaReference reference) {
        try {
            // 确保 ProxyFactory 已初始化
            if (proxyFactory == null) {
                proxyFactory = applicationContext.getBean(ProxyFactory.class);
            }

            Class<?> interfaceClass = field.getType();

            // 从注解中读取所有参数
            String version = reference.version();
            long timeout = reference.timeout();
            boolean async = reference.async();
            String cluster = reference.cluster();
            int retries = reference.retries();

            // 熔断器配置
            boolean enableCircuitBreaker = reference.circuitBreaker();
            int circuitBreakerThreshold = reference.circuitBreakerThreshold();
            long circuitBreakerTimeout = reference.circuitBreakerTimeout();

            // 限流器配置
            boolean enableRateLimit = reference.rateLimit();
            int rateLimitPermits = reference.rateLimitPermits();

            log.info("🔧 [Lumina-RPC] Injecting @LuminaReference proxy for: {}.{} (interface: {}, async: {}, cluster: {}, retries: {}, circuitBreaker: {}, rateLimit: {})",
                    bean.getClass().getSimpleName(),
                    field.getName(),
                    interfaceClass.getName(),
                    async,
                    cluster,
                    retries,
                    enableCircuitBreaker,
                    enableRateLimit);

            // 使用 ProxyFactory 创建动态代理
            Object proxy = proxyFactory.createProxy(
                    interfaceClass, version, timeout, async, cluster, retries,
                    enableCircuitBreaker, circuitBreakerThreshold, circuitBreakerTimeout,
                    enableRateLimit, rateLimitPermits
            );

            // 设置字段可访问并注入代理
            ReflectionUtils.makeAccessible(field);
            field.set(bean, proxy);

            log.info("✅ [Lumina-RPC] Successfully injected proxy for {}.{}",
                    bean.getClass().getSimpleName(), field.getName());

        } catch (Exception e) {
            log.error("❌ [Lumina-RPC] Failed to inject @LuminaReference for field: {}.{}",
                    bean.getClass().getSimpleName(), field.getName(), e);
            throw new RuntimeException("Failed to inject LuminaReference for field: " + field.getName(), e);
        }
    }
}
