package com.lumina.rpc.core.annotation;

import java.lang.annotation.*;

/**
 * Lumina RPC 服务消费者注解
 *
 * 标注在 Consumer 的接口字段上，用于注入代理对象
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LuminaReference {

    /**
     * 服务接口类
     *
     * @return 服务接口 Class
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务版本号
     *
     * @return 版本号
     */
    String version() default "";

    /**
     * 调用超时时间（毫秒）
     *
     * @return 超时时间
     */
    long timeout() default 5000;

    /**
     * 重试次数
     *
     * @return 重试次数
     */
    int retries() default 3;

    /**
     * 负载均衡策略
     *
     * @return 负载均衡策略名称
     */
    String loadBalance() default "round-robin";

    /**
     * 是否异步调用
     *
     * 为 true 时，返回类型应为 CompletableFuture<T>
     *
     * @return 是否异步
     */
    boolean async() default false;

    /**
     * 集群容错策略
     *
     * 可选值：
     * - failover: 失败自动重试其他服务器（默认）
     * - failfast: 快速失败，只发起一次调用
     * - failsafe: 失败安全，异常直接忽略
     * - forking: 并行调用多个服务器，一个成功即返回
     *
     * @return 集群策略名称
     */
    String cluster() default "failover";

    // ==================== 熔断器配置 ====================

    /**
     * 是否启用熔断器
     *
     * @return true 启用，false 禁用
     */
    boolean circuitBreaker() default true;

    /**
     * 熔断器错误率阈值（百分比）
     *
     * 当错误率达到此阈值时，熔断器打开
     *
     * @return 错误率阈值（如 50 表示 50%）
     */
    int circuitBreakerThreshold() default 50;

    /**
     * 熔断器恢复时间（毫秒）
     *
     * 熔断器打开后，经过此时间后进入半开状态
     *
     * @return 恢复时间
     */
    long circuitBreakerTimeout() default 30000;

    // ==================== 限流器配置 ====================

    /**
     * 是否启用限流
     *
     * @return true 启用，false 禁用
     */
    boolean rateLimit() default false;

    /**
     * 限流阈值（每秒请求数）
     *
     * 仅当 rateLimit=true 时生效
     *
     * @return 每秒允许的最大请求数
     */
    int rateLimitPermits() default 100;
}
