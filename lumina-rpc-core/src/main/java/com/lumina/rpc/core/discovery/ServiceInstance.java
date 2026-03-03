package com.lumina.rpc.core.discovery;

import java.util.Objects;

/**
 * 服务实例
 * 表示一个可用的 RPC 服务提供者
 */
public class ServiceInstance {

    private String serviceName;
    private String host;
    private int port;
    private String version;
    private boolean healthy;

    public ServiceInstance() {
    }

    public ServiceInstance(String serviceName, String host, int port) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.version = "";
        this.healthy = true;
    }

    public ServiceInstance(String serviceName, String host, int port, String version) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.version = version;
        this.healthy = true;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    /**
     * 获取服务地址 key (host:port)
     */
    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "serviceName='" + serviceName + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", version='" + version + '\'' +
                ", healthy=" + healthy +
                '}';
    }
}