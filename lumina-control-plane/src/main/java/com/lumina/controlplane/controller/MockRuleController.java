package com.lumina.controlplane.controller;

import com.lumina.controlplane.entity.MockRuleEntity;
import com.lumina.controlplane.service.MockRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock Rule 管理控制器
 * 提供规则的增删改查接口
 */
@RestController
@RequestMapping("/api/v1/rules")
public class MockRuleController {

    private static final Logger logger = LoggerFactory.getLogger(MockRuleController.class);

    private final MockRuleService mockRuleService;

    public MockRuleController(MockRuleService mockRuleService) {
        this.mockRuleService = mockRuleService;
    }

    /**
     * 获取所有规则
     */
    @GetMapping
    public ResponseEntity<List<MockRuleEntity>> getAllRules() {
        logger.debug("Getting all mock rules");
        return ResponseEntity.ok(mockRuleService.findAll());
    }

    /**
     * 根据 ID 获取规则
     */
    @GetMapping("/{id}")
    public ResponseEntity<MockRuleEntity> getRuleById(@PathVariable("id")  Long id) {
        logger.debug("Getting mock rule by id: {}", id);
        return mockRuleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据服务名获取启用的规则
     */
    @GetMapping("/service/{serviceName}")
    public ResponseEntity<List<MockRuleEntity>> getRulesByService(
            @PathVariable("serviceName") String serviceName) {
        logger.debug("Getting mock rules for service: {}", serviceName);
        return ResponseEntity.ok(mockRuleService.findByServiceNameAndEnabled(serviceName));
    }

    /**
     * 根据服务名和方法名获取规则
     */
    @GetMapping("/service/{serviceName}/method/{methodName}")
    public ResponseEntity<List<MockRuleEntity>> getRulesByServiceAndMethod(
            @PathVariable("serviceName") String serviceName,
            @PathVariable("methodName") String methodName) {
        logger.debug("Getting mock rules for service: {}, method: {}", serviceName, methodName);
        return ResponseEntity.ok(mockRuleService.findByServiceAndMethod(serviceName, methodName));
    }

    /**
     * 创建规则
     */
    @PostMapping
    public ResponseEntity<MockRuleEntity> createRule(@RequestBody MockRuleEntity rule) {
        logger.info("Creating new mock rule for service: {}", rule.getServiceName());
        MockRuleEntity created = mockRuleService.createRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    public ResponseEntity<MockRuleEntity> updateRule(
            @PathVariable("id") Long id,
            @RequestBody MockRuleEntity rule) {
        logger.info("Updating mock rule with id: {}", id);
        // 明确将路径参数 id 赋值给接收到的 Entity，确保 JPA 正确识别为更新操作
        rule.setId(id);
        MockRuleEntity updated = mockRuleService.updateRule(id, rule);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable("id") Long id) {
        logger.info("Deleting mock rule with id: {}", id);
        mockRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 切换规则启用状态
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<MockRuleEntity> toggleRuleEnabled(@PathVariable("id") Long id) {
        logger.info("Toggling enabled status for rule id: {}", id);
        MockRuleEntity toggled = mockRuleService.toggleEnabled(id);
        return ResponseEntity.ok(toggled);
    }

    /**
     * 批量导入规则
     */
    @PostMapping("/batch")
    public ResponseEntity<List<MockRuleEntity>> batchCreateRules(
            @RequestBody List<MockRuleEntity> rules) {
        logger.info("Batch creating {} mock rules", rules.size());
        List<MockRuleEntity> created = rules.stream()
                .map(mockRuleService::createRule)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        logger.debug("Getting rule statistics");
        List<MockRuleEntity> allRules = mockRuleService.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allRules.size());
        stats.put("enabled", allRules.stream().filter(MockRuleEntity::getEnabled).count());
        stats.put("disabled", allRules.stream().filter(r -> !r.getEnabled()).count());

        return ResponseEntity.ok(stats);
    }
}
