package com.lumina.sample.engine.controller;

import com.lumina.rpc.core.shutdown.GracefulShutdownManager;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 优雅停机测试控制器
 *
 * 用于演示和测试优雅停机功能：
 * 1. 查看当前停机状态
 * 2. 查看正在处理的请求数量
 * 3. 手动触发停机
 */
@RestController
@RequestMapping("/api/shutdown")
public class ShutdownController {

    /**
     * 获取停机状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
        Map<String, Object> status = new HashMap<>();
        status.put("shuttingDown", manager.isShuttingDown());
        status.put("activeRequests", manager.getActiveRequestCount());
        return status;
    }

    /**
     * 手动触发优雅停机（仅用于测试）
     *
     * 调用后服务将：
     * 1. 从注册中心注销
     * 2. 拒绝新请求
     * 3. 等待正在处理的请求完成
     * 4. 关闭服务
     */
    @PostMapping("/trigger")
    public Map<String, Object> triggerShutdown() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Graceful shutdown triggered");
        result.put("activeRequests", manager.getActiveRequestCount());

        // 异步执行停机，让响应能返回
        new Thread(() -> {
            try {
                Thread.sleep(100); // 等待响应返回
                manager.gracefulShutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-trigger").start();

        return result;
    }
}