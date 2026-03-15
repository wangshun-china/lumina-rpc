package com.lumina.rpc.core.exception;

/**
 * 限流异常
 *
 * 当请求被限流器拒绝时抛出
 *
 * @author Lumina-RPC Team
 * @since 1.3.0
 */
public class RateLimitException extends RuntimeException {

    /** 服务名称 */
    private final String serviceName;

    /** 当前 QPS 阈值 */
    private final int permitsPerSecond;

    public RateLimitException(String serviceName, int permitsPerSecond) {
        super(String.format("Rate limit exceeded for service: %s (limit: %d/s)", serviceName, permitsPerSecond));
        this.serviceName = serviceName;
        this.permitsPerSecond = permitsPerSecond;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getPermitsPerSecond() {
        return permitsPerSecond;
    }
}