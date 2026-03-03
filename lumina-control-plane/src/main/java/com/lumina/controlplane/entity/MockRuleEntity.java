package com.lumina.controlplane.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lumina_mock_rule")
public class MockRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "method_name", nullable = false, length = 255)
    private String methodName;

    @Column(name = "match_type", length = 50)
    private String matchType = "exact";

    /**
     * 条件规则 - JSON 格式（支持多条件列表）
     * 新格式：[{"index": 0, "value": "USS-1701"}, {"index": 1, "value": "Sector-Alpha"}]
     * 旧格式：{"argIndex":0, "matchValue":"USS-1701"}
     * 为空代表无条件触发
     */
    @Column(name = "condition_rule", columnDefinition = "TEXT")
    private String conditionRule;

    /**
     * Mock 类型
     * - SHORT_CIRCUIT: 直接阻断，返回 Mock 数据
     * - TAMPER: 篡改真实数据，将 Mock 数据与真实响应合并
     */
    @Column(name = "mock_type", length = 50)
    private String mockType = "SHORT_CIRCUIT";

    @Column(name = "match_condition", length = 1000)
    private String matchCondition;

    @Column(name = "response_type", nullable = false, length = 50)
    private String responseType = "success";

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_delay_ms")
    private Integer responseDelayMs = 0;

    @Column(name = "http_status")
    private Integer httpStatus = 200;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "tags", length = 255)
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getConditionRule() {
        return conditionRule;
    }

    public void setConditionRule(String conditionRule) {
        this.conditionRule = conditionRule;
    }

    public String getMockType() {
        return mockType;
    }

    public void setMockType(String mockType) {
        this.mockType = mockType;
    }

    public String getMatchCondition() {
        return matchCondition;
    }

    public void setMatchCondition(String matchCondition) {
        this.matchCondition = matchCondition;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getResponseDelayMs() {
        return responseDelayMs;
    }

    public void setResponseDelayMs(Integer responseDelayMs) {
        this.responseDelayMs = responseDelayMs;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
