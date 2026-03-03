package com.lumina.rpc.core.exception;

/**
 * 无可用服务提供者异常
 * 当服务发现无法找到指定服务的可用提供者时抛出
 */
public class NoProviderAvailableException extends RuntimeException {

    private final String serviceName;

    public NoProviderAvailableException(String serviceName) {
        super("找不到可用的服务提供者: " + serviceName);
        this.serviceName = serviceName;
    }

    public NoProviderAvailableException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    public NoProviderAvailableException(String serviceName, Throwable cause) {
        super("找不到可用的服务提供者: " + serviceName, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}