package com.lumina.rpc.core.exception;

/**
 * 熔断器异常
 *
 * 当熔断器处于 OPEN 状态时抛出
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class CircuitBreakerException extends RuntimeException {

    /** 服务名称 */
    private final String serviceName;

    /** 熔断器状态 */
    private final String state;

    public CircuitBreakerException(String serviceName) {
        super(String.format("Circuit breaker is OPEN for service: %s", serviceName));
        this.serviceName = serviceName;
        this.state = "OPEN";
    }

    public CircuitBreakerException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
        this.state = "OPEN";
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getState() {
        return state;
    }
}