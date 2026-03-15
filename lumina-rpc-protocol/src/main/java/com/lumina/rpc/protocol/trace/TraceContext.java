package com.lumina.rpc.protocol.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 链路追踪上下文
 *
 * 使用 ThreadLocal 存储当前请求的 Trace ID
 * 支持在日志中打印 Trace ID 进行链路追踪
 */
public class TraceContext {

    private static final Logger logger = LoggerFactory.getLogger(TraceContext.class);

    /** 当前线程的 Trace ID */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /** 当前线程的 Span ID（用于多级调用） */
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();

    /**
     * 生成新的 Trace ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 设置当前 Trace ID
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
        logger.debug("Set traceId: {}", traceId);
    }

    /**
     * 获取当前 Trace ID
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 设置当前 Span ID
     */
    public static void setSpanId(String spanId) {
        SPAN_ID.set(spanId);
    }

    /**
     * 获取当前 Span ID
     */
    public static String getSpanId() {
        return SPAN_ID.get();
    }

    /**
     * 清除上下文（请求结束时调用）
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
    }

    /**
     * 检查是否存在 Trace ID
     */
    public static boolean hasTraceId() {
        return TRACE_ID.get() != null;
    }

    /**
     * 生成下一个 Span ID
     */
    public static String nextSpanId() {
        String current = SPAN_ID.get();
        if (current == null) {
            return "1";
        }
        return current + ".1";
    }
}