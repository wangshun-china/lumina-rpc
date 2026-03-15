package com.lumina.rpc.core.trace;

import com.lumina.rpc.protocol.trace.Span;
import com.lumina.rpc.protocol.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Span 收集器
 *
 * 在调用前后收集 Span 数据，支持 Consumer 端和 Provider 端
 */
public class SpanCollector {

    private static final Logger logger = LoggerFactory.getLogger(SpanCollector.class);

    private static final SpanCollector INSTANCE = new SpanCollector();

    // 存储正在进行的 Span，key: spanId
    private final ConcurrentHashMap<String, Span> activeSpans = new ConcurrentHashMap<>();

    private SpanCollector() {
    }

    public static SpanCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 开始一个 Client Span
     *
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param address     远程地址
     * @return Span 对象
     */
    public Span startClientSpan(String serviceName, String methodName, InetSocketAddress address) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = TraceContext.generateTraceId();
            TraceContext.setTraceId(traceId);
        }

        String parentSpanId = TraceContext.getSpanId();
        String spanId = TraceContext.nextSpanId();
        TraceContext.setSpanId(spanId);

        Span span = new Span(traceId, spanId, parentSpanId, serviceName, methodName, "CLIENT");
        span.setRemoteAddress(address != null ? address.getHostString() + ":" + address.getPort() : "unknown");

        activeSpans.put(spanId, span);

        if (logger.isDebugEnabled()) {
            logger.debug("[SpanCollector] Started CLIENT span: {} - {} (traceId: {})",
                    serviceName, methodName, traceId);
        }

        return span;
    }

    /**
     * 开始一个 Server Span
     *
     * @param traceId     Trace ID
     * @param serviceName 服务名称
     * @param methodName  方法名称
     * @param remoteAddr  远程地址
     * @return Span 对象
     */
    public Span startServerSpan(String traceId, String serviceName, String methodName, String remoteAddr) {
        String parentSpanId = TraceContext.getSpanId();
        String spanId = TraceContext.nextSpanId();
        TraceContext.setTraceId(traceId);
        TraceContext.setSpanId(spanId);

        Span span = new Span(traceId, spanId, parentSpanId, serviceName, methodName, "SERVER");
        span.setRemoteAddress(remoteAddr);

        activeSpans.put(spanId, span);

        if (logger.isDebugEnabled()) {
            logger.debug("[SpanCollector] Started SERVER span: {} - {} (traceId: {})",
                    serviceName, methodName, traceId);
        }

        return span;
    }

    /**
     * 结束 Span
     *
     * @param span Span 对象
     */
    public void endSpan(Span span) {
        if (span == null) {
            return;
        }

        span.finish();
        activeSpans.remove(span.getSpanId());

        if (logger.isDebugEnabled()) {
            logger.debug("[SpanCollector] Ended span: {} (duration: {}ms)",
                    span.getSpanId(), span.getDuration());
        }

        // 异步上报 Span
        TraceReporter.getInstance().reportSpan(span);
    }

    /**
     * 结束 Span（带错误信息）
     *
     * @param span         Span 对象
     * @param errorMessage 错误信息
     */
    public void endSpanWithError(Span span, String errorMessage) {
        if (span == null) {
            return;
        }

        span.error(errorMessage);
        endSpan(span);
    }

    /**
     * 获取当前活跃的 Span
     *
     * @return Span 对象
     */
    public Span getCurrentSpan() {
        String spanId = TraceContext.getSpanId();
        return spanId != null ? activeSpans.get(spanId) : null;
    }

    /**
     * 获取活跃 Span 数量
     */
    public int getActiveSpanCount() {
        return activeSpans.size();
    }
}