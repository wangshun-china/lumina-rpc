package com.lumina.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.controlplane.entity.MockRuleEntity;
import com.lumina.controlplane.mapper.MockRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Mock Rule 服务层
 */
@Service
public class MockRuleService {

    private static final Logger logger = LoggerFactory.getLogger(MockRuleService.class);

    private final MockRuleMapper mapper;
    private final SseBroadcastService sseBroadcastService;
    private final ObjectMapper objectMapper;

    public MockRuleService(MockRuleMapper mapper,
                           SseBroadcastService sseBroadcastService,
                           ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.sseBroadcastService = sseBroadcastService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MockRuleEntity createRule(MockRuleEntity rule) {
        logger.info("Creating mock rule for service: {}, method: {}",
                rule.getServiceName(), rule.getMethodName());

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

        LocalDateTime now = LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);

        mapper.insert(rule);

        sseBroadcastService.broadcastRuleChange(
                rule.getServiceName(),
                rule.getId(),
                "CREATE"
        );

        logger.info("Created mock rule with id: {}", rule.getId());
        return rule;
    }

    @Transactional
    public MockRuleEntity updateRule(Long id, MockRuleEntity updatedRule) {
        logger.info("Updating mock rule with id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity existingRule = mapper.selectOneById(id);
        if (existingRule == null) {
            throw new RuntimeException("Rule not found with id: " + id);
        }

        String oldServiceName = existingRule.getServiceName();

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
        existingRule.setUpdatedAt(LocalDateTime.now());

        mapper.update(existingRule);
        logger.info("Updated mock rule with id: {}", existingRule.getId());

        try {
            if (!oldServiceName.equals(existingRule.getServiceName())) {
                sseBroadcastService.broadcastRuleChange(oldServiceName, existingRule.getId(), "UPDATE");
            }
            sseBroadcastService.broadcastRuleChange(existingRule.getServiceName(), existingRule.getId(), "UPDATE");
        } catch (Exception e) {
            logger.error("Failed to broadcast rule change", e);
        }

        return existingRule;
    }

    @Transactional
    public void deleteRule(Long id) {
        logger.info("Deleting mock rule with id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity rule = mapper.selectOneById(id);
        if (rule == null) {
            throw new RuntimeException("Rule not found with id: " + id);
        }

        String serviceName = rule.getServiceName();

        mapper.deleteById(id);

        sseBroadcastService.broadcastRuleChange(serviceName, id, "DELETE");

        logger.info("Deleted mock rule with id: {}", id);
    }

    public Optional<MockRuleEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    public List<MockRuleEntity> findAll() {
        return mapper.selectAll();
    }

    public List<MockRuleEntity> findByServiceNameAndEnabled(String serviceName) {
        return mapper.findByServiceNameAndEnabledTrue(serviceName);
    }

    public List<MockRuleEntity> findByServiceAndMethod(String serviceName, String methodName) {
        return mapper.findByServiceNameAndMethodNameAndEnabledTrue(serviceName, methodName);
    }

    @Transactional
    public MockRuleEntity toggleEnabled(Long id) {
        logger.info("Toggling rule enabled state for id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }

        MockRuleEntity rule = mapper.selectOneById(id);
        if (rule == null) {
            throw new RuntimeException("Rule not found with id: " + id);
        }

        rule.setEnabled(!rule.getEnabled());
        rule.setUpdatedAt(LocalDateTime.now());
        mapper.update(rule);

        String action = rule.getEnabled() ? "ENABLE" : "DISABLE";
        sseBroadcastService.broadcastRuleChange(rule.getServiceName(), rule.getId(), action);

        return rule;
    }

    public List<MockRuleEntity> findAllEnabled() {
        return mapper.findByEnabledTrue();
    }

    public long countAll() {
        return mapper.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create());
    }

    public long countEnabledRules() {
        return mapper.countByEnabledTrue();
    }
}