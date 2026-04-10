package com.lumina.rpc.core.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.rpc.protocol.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Trace 上报器
 *
 * 将 Span 数据异步上报到控制面
 */
public class TraceReporter {

    private static final Logger logger = LoggerFactory.getLogger(TraceReporter.class);

    private static final TraceReporter INSTANCE = new TraceReporter();

    // 控制面地址
    private String controlPlaneUrl = "http://localhost:8080";

    // HTTP 客户端
    private final HttpClient httpClient;

    // JSON 序列化器
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 异步上报线程池
    private final ExecutorService executor;

    // 是否启用上报
    private volatile boolean enabled = true;

    private TraceReporter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.executor = new ThreadPoolExecutor(
                1, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "trace-reporter");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    public static TraceReporter getInstance() {
        return INSTANCE;
    }

    /**
     * 设置控制面地址
     */
    public void setControlPlaneUrl(String url) {
        this.controlPlaneUrl = url;
    }

    /**
     * 设置是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 上报 Span（异步）
     */
    public void reportSpan(Span span) {
        if (!enabled || span == null) {
            return;
        }

        executor.submit(() -> {
            try {
                doReport(span);
            } catch (Exception e) {
                logger.debug("Failed to report span: {}", e.getMessage());
            }
        });
    }

    /**
     * 执行上报
     */
    private void doReport(Span span) throws Exception {
        String json = objectMapper.writeValueAsString(span);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(controlPlaneUrl + "/api/v1/traces/spans"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            logger.debug("Reported span: {} (status: {})", span.getSpanId(), response.statusCode());
        } else {
            logger.debug("Failed to report span: {} (status: {})", span.getSpanId(), response.statusCode());
        }
    }

    }