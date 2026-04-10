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
 * 在调用前后收集 Span 数据
 */
public class SpanCollector {

    private static final Logger logger = LoggerFactory.getLogger(SpanCollector.class);

    private static final SpanCollector INSTANCE = new SpanCollector();

    private final ConcurrentHashMap<String, Span> activeSpans = new ConcurrentHashMap<>();

    private SpanCollector() {
    }

    public static SpanCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 开始一个 Client Span
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
     * 结束 Span
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

        TraceReporter.getInstance().reportSpan(span);
    }

    /**
     * 结束 Span（带错误信息）
     */
    public void endSpanWithError(Span span, String errorMessage) {
        if (span == null) {
            return;
        }

        span.error(errorMessage);
        endSpan(span);
    }
}