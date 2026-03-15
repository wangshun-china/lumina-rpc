package com.lumina.rpc.protocol;

import java.io.Serializable;

/**
 * RPC 响应对象
 */
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // 请求ID，用于匹配请求
    private long requestId;

    // 链路追踪ID
    private String traceId;

    // 响应码
    private int code;

    // 错误信息
    private String message;

    // 响应数据
    private Object data;

    // 响应码常量
    public static final int SUCCESS = 200;
    public static final int ERROR = 500;
    public static final int NOT_FOUND = 404;
    public static final int TIMEOUT = 408;

    public RpcResponse() {
    }

    public static RpcResponse success(long requestId, Object data) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setCode(SUCCESS);
        response.setData(data);
        return response;
    }

    public static RpcResponse success(long requestId, String traceId, Object data) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setTraceId(traceId);
        response.setCode(SUCCESS);
        response.setData(data);
        return response;
    }

    public static RpcResponse error(long requestId, String message) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setCode(ERROR);
        response.setMessage(message);
        return response;
    }

    public static RpcResponse error(long requestId, String traceId, String message) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setTraceId(traceId);
        response.setCode(ERROR);
        response.setMessage(message);
        return response;
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

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId=" + requestId +
                ", traceId='" + traceId + '\'' +
                ", code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}