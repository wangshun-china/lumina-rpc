package com.lumina.rpc.protocol;

import java.io.Serializable;
import java.util.Arrays;

/**
 * RPC 请求对象
 */
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // 请求ID，用于匹配响应
    private long requestId;

    // 链路追踪ID，用于分布式追踪
    private String traceId;

    // 接口全限定名
    private String interfaceName;

    // 方法名
    private String methodName;

    // 参数类型数组
    private Class<?>[] parameterTypes;

    // 参数值数组
    private Object[] parameters;

    // 服务版本
    private String version = "";

    public RpcRequest() {
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "RpcRequest{" +
                "requestId=" + requestId +
                ", traceId='" + traceId + '\'' +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameterTypes=" + Arrays.toString(parameterTypes) +
                ", parameters=" + Arrays.toString(parameters) +
                ", version='" + version + '\'' +
                '}';
    }
}