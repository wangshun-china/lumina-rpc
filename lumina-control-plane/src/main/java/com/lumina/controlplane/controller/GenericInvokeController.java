package com.lumina.controlplane.controller;

import com.lumina.controlplane.service.GenericInvokeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 泛化调用控制器 (RPC Postman)
 *
 * 允许控制台直接调用微服务，无需编写客户端代码
 * 支持 HTTP -> RPC 的桥接调用
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/registry")
public class GenericInvokeController {

    private static final Logger logger = LoggerFactory.getLogger(GenericInvokeController.class);

    private final GenericInvokeService genericInvokeService;

    public GenericInvokeController(GenericInvokeService genericInvokeService) {
        this.genericInvokeService = genericInvokeService;
    }

    /**
     * 泛化调用接口
     *
     * @param request 调用请求
     * @return 调用结果
     */
    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody InvokeRequest request) {
        logger.info("📨 收到泛化调用请求: {}.{}", request.getServiceName(), request.getMethodName());

        Map<String, Object> result = new HashMap<>();

        try {
            // 参数校验
            if (request.getServiceName() == null || request.getServiceName().isEmpty()) {
                result.put("success", false);
                result.put("error", "serviceName is required");
                return ResponseEntity.badRequest().body(result);
            }

            if (request.getMethodName() == null || request.getMethodName().isEmpty()) {
                result.put("success", false);
                result.put("error", "methodName is required");
                return ResponseEntity.badRequest().body(result);
            }

            // 执行泛化调用
            Object response = genericInvokeService.invoke(
                    request.getServiceName(),
                    request.getMethodName(),
                    request.getArgs(),
                    request.getTimeout()
            );

            result.put("success", true);
            result.put("data", response);
            result.put("serviceName", request.getServiceName());
            result.put("methodName", request.getMethodName());

            logger.info("✅ 泛化调用成功: {}.{}", request.getServiceName(), request.getMethodName());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("❌ 泛化调用失败: {}.{} - {}", request.getServiceName(), request.getMethodName(), e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("serviceName", request.getServiceName());
            result.put("methodName", request.getMethodName());

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 获取服务方法列表
     *
     * @param serviceName 服务名称
     * @return 方法列表
     */
    @GetMapping("/metadata/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceMetadata(@PathVariable("serviceName") String serviceName) {
        logger.debug("查询服务元数据: {}", serviceName);

        Map<String, Object> result = genericInvokeService.getServiceMetadata(serviceName);

        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 泛化调用请求
     */
    public static class InvokeRequest {
        private String serviceName;
        private String methodName;
        private Object[] args;
        private Long timeout = 5000L;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public Object[] getArgs() {
            return args;
        }

        public void setArgs(Object[] args) {
            this.args = args;
        }

        public Long getTimeout() {
            return timeout;
        }

        public void setTimeout(Long timeout) {
            this.timeout = timeout;
        }
    }
}