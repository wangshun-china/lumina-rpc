package com.lumina.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.controlplane.entity.MockRuleEntity;
import com.lumina.controlplane.repository.MockRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Mock Rule 服务层
 * 处理规则 CRUD 并触发 SSE 广播
 */
@Service
public class MockRuleService {

    private static final Logger logger = LoggerFactory.getLogger(MockRuleService.class);

    private final MockRuleRepository ruleRepository;
    private final SseBroadcastService sseBroadcastService;
    private final ObjectMapper objectMapper;

    public MockRuleService(MockRuleRepository ruleRepository,
                           SseBroadcastService sseBroadcastService,
                           ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.sseBroadcastService = sseBroadcastService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建规则
     */
    @Transactional
    public MockRuleEntity createRule(MockRuleEntity rule) {
        logger.info("Creating mock rule for service: {}, method: {}",
                rule.getServiceName(), rule.getMethodName());

        // 设置默认值
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getResponseDelayMs() == null) {
            rule.setResponseDelayMs(0);
        }
        if (rule.getHttpStatus() == null) {
            rule.setHttpStatus(200);
        }

        MockRuleEntity savedRule = ruleRepository.save(rule);

        // 广播规则变更
        sseBroadcastService.broadcastRuleChange(
                savedRule.getServiceName(),
                savedRule.getId(),
                "CREATE"
        );

        logger.info("Created mock rule with id: {}", savedRule.getId());
        return savedRule;
    }

    /**
     * 更新规则 - 暴力覆盖法
     * 解决 JPA 游离态导致的更新不生效问题
     */
    @Transactional
    public MockRuleEntity updateRule(Long id, MockRuleEntity updatedRule) {
        logger.info("Force updating mock rule with id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        // 第一步：先查询出数据库中已存在的托管实体
        MockRuleEntity existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found with id: " + id));

        String oldServiceName = existingRule.getServiceName();
        Long ruleId = existingRule.getId();

        // 第二步：暴力覆盖所有字段（不管是否为 null，直接设置）
        existingRule.setServiceName(updatedRule.getServiceName());
        existingRule.setMethodName(updatedRule.getMethodName());
        existingRule.setMatchType(updatedRule.getMatchType());
        existingRule.setConditionRule(updatedRule.getConditionRule());
        existingRule.setMockType(updatedRule.getMockType());
        existingRule.setMatchCondition(updatedRule.getMatchCondition());
        existingRule.setResponseType(updatedRule.getResponseType());
        existingRule.setResponseBody(updatedRule.getResponseBody());
        existingRule.setResponseDelayMs(updatedRule.getResponseDelayMs());
        existingRule.setHttpStatus(updatedRule.getHttpStatus());
        existingRule.setEnabled(updatedRule.getEnabled());
        existingRule.setPriority(updatedRule.getPriority());
        existingRule.setDescription(updatedRule.getDescription());
        existingRule.setTags(updatedRule.getTags());

        // 第三步：强制保存（merge）
        MockRuleEntity savedRule = ruleRepository.saveAndFlush(existingRule);
        logger.info("Force updated mock rule with id: {}, service: {}", savedRule.getId(), savedRule.getServiceName());

        // 第四步：触发 SSE 广播
        try {
            if (!oldServiceName.equals(savedRule.getServiceName())) {
                sseBroadcastService.broadcastRuleChange(oldServiceName, savedRule.getId(), "UPDATE");
            }
            sseBroadcastService.broadcastRuleChange(savedRule.getServiceName(), savedRule.getId(), "UPDATE");
            logger.info("SSE broadcast sent for rule id: {}", savedRule.getId());
        } catch (Exception e) {
            logger.error("Failed to broadcast rule change", e);
        }

        return savedRule;
    }

    /**
     * 删除规则
     */
    @Transactional
    public void deleteRule(Long id) {
        logger.info("Deleting mock rule with id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found with id: " + id));

        String serviceName = rule.getServiceName();

        ruleRepository.delete(rule);

        // 广播规则删除
        sseBroadcastService.broadcastRuleChange(serviceName, id, "DELETE");

        logger.info("Deleted mock rule with id: {}", id);
    }

    /**
     * 根据 ID 查询规则
     */
    public Optional<MockRuleEntity> findById(Long id) {
        return ruleRepository.findById(id);
    }

    /**
     * 查询所有规则
     */
    public List<MockRuleEntity> findAll() {
        return ruleRepository.findAll();
    }

    /**
     * 根据服务名查询启用的规则
     */
    public List<MockRuleEntity> findByServiceNameAndEnabled(String serviceName) {
        return ruleRepository.findByServiceNameAndEnabledTrue(serviceName);
    }

    /**
     * 根据服务名和方法名查询启用的规则
     */
    public List<MockRuleEntity> findByServiceAndMethod(String serviceName, String methodName) {
        return ruleRepository.findByServiceNameAndMethodNameAndEnabledTrue(serviceName, methodName);
    }

    /**
     * 切换规则启用状态
     */
    @Transactional
    public MockRuleEntity toggleEnabled(Long id) {
        logger.info("Toggling rule enabled state for id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found with id: " + id));

        rule.setEnabled(!rule.getEnabled());
        MockRuleEntity saved = ruleRepository.save(rule);

        // 广播状态变更
        String action = saved.getEnabled() ? "ENABLE" : "DISABLE";
        sseBroadcastService.broadcastRuleChange(saved.getServiceName(), saved.getId(), action);

        return saved;
    }

    /**
     * 获取所有启用的规则（Consumer 初始化时使用）
     */
    public List<MockRuleEntity> findAllEnabled() {
        return ruleRepository.findByEnabledTrue();
    }

    /**
     * 统计所有规则数量
     */
    public long countAll() {
        return ruleRepository.count();
    }

    /**
     * 统计启用的规则数量
     */
    public long countEnabledRules() {
        return ruleRepository.countByEnabledTrue();
    }
}
