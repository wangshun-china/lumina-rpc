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
     * 更新规则
     */
    @Transactional
    public MockRuleEntity updateRule(Long id, MockRuleEntity ruleUpdate) {
        logger.info("Updating mock rule with id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity existingRule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found with id: " + id));

        String oldServiceName = existingRule.getServiceName();

        // 更新字段
        if (ruleUpdate.getServiceName() != null) {
            existingRule.setServiceName(ruleUpdate.getServiceName());
        }
        if (ruleUpdate.getMethodName() != null) {
            existingRule.setMethodName(ruleUpdate.getMethodName());
        }
        if (ruleUpdate.getMatchType() != null) {
            existingRule.setMatchType(ruleUpdate.getMatchType());
        }
        if (ruleUpdate.getMatchCondition() != null) {
            existingRule.setMatchCondition(ruleUpdate.getMatchCondition());
        }
        if (ruleUpdate.getResponseType() != null) {
            existingRule.setResponseType(ruleUpdate.getResponseType());
        }
        if (ruleUpdate.getResponseBody() != null) {
            existingRule.setResponseBody(ruleUpdate.getResponseBody());
        }
        if (ruleUpdate.getResponseDelayMs() != null) {
            existingRule.setResponseDelayMs(ruleUpdate.getResponseDelayMs());
        }
        if (ruleUpdate.getHttpStatus() != null) {
            existingRule.setHttpStatus(ruleUpdate.getHttpStatus());
        }
        if (ruleUpdate.getEnabled() != null) {
            existingRule.setEnabled(ruleUpdate.getEnabled());
        }
        if (ruleUpdate.getPriority() != null) {
            existingRule.setPriority(ruleUpdate.getPriority());
        }
        if (ruleUpdate.getDescription() != null) {
            existingRule.setDescription(ruleUpdate.getDescription());
        }
        if (ruleUpdate.getTags() != null) {
            existingRule.setTags(ruleUpdate.getTags());
        }

        MockRuleEntity savedRule = ruleRepository.save(existingRule);

        // 如果服务名称改变，需要同时通知旧服务和新服务
        if (!oldServiceName.equals(savedRule.getServiceName())) {
            sseBroadcastService.broadcastRuleChange(oldServiceName, savedRule.getId(), "UPDATE");
        }
        sseBroadcastService.broadcastRuleChange(savedRule.getServiceName(), savedRule.getId(), "UPDATE");

        logger.info("Updated mock rule with id: {}", savedRule.getId());
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
