package com.lumina.rpc.core.mock;

import java.io.Serializable;

/**
 * Mock 规则数据结构
 *
 * 定义 RPC 服务的动态降级规则
 *
 * 企业级特性：
 * 1. 条件匹配：支持参数匹配，只有符合条件的调用才触发 Mock
 * 2. 双模引擎：SHORT_CIRCUIT（直接阻断）和 TAMPER（篡改真实数据）
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
public class MockRule implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Mock 类型枚举
     */
    public enum MockType {
        /**
         * 短路模式：直接返回 Mock 数据，不发起真实调用
         */
        SHORT_CIRCUIT,
        /**
         * 篡改模式：先发起真实调用，然后将 Mock 数据与真实响应合并
         */
        TAMPER
    }

    /**
     * 方法名称（支持 "*" 通配符匹配所有方法）
     */
    private String methodName;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * Mock 类型
     */
    private MockType mockType = MockType.SHORT_CIRCUIT;

    /**
     * 条件规则 JSON
     * 例如：{"argIndex":0, "matchValue":"USS-1701"} 表示第一个参数等于 "USS-1701" 时触发
     * 为空或 null 表示无条件触发
     */
    private String conditionRule;

    /**
     * 模拟延迟（毫秒）
     */
    private long delayMs = 0;

    /**
     * 响应数据（将被转换为方法返回类型）
     */
    private Object responseData;

    /**
     * 响应数据 JSON 字符串（用于篡改模式合并）
     */
    private String responseDataJson;

    /**
     * 是否抛出异常
     */
    private boolean throwException = false;

    /**
     * 异常消息
     */
    private String exceptionMessage = "Mock exception triggered";

    /**
     * 规则优先级（数字越大优先级越高）
     */
    private int priority = 0;

    /**
     * 规则描述
     */
    private String description;

    // ==================== Getters & Setters ====================

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MockType getMockType() {
        return mockType;
    }

    public void setMockType(MockType mockType) {
        this.mockType = mockType;
    }

    public String getConditionRule() {
        return conditionRule;
    }

    public void setConditionRule(String conditionRule) {
        this.conditionRule = conditionRule;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public Object getResponseData() {
        return responseData;
    }

    public void setResponseData(Object responseData) {
        this.responseData = responseData;
    }

    public String getResponseDataJson() {
        return responseDataJson;
    }

    public void setResponseDataJson(String responseDataJson) {
        this.responseDataJson = responseDataJson;
    }

    public boolean isThrowException() {
        return throwException;
    }

    public void setThrowException(boolean throwException) {
        this.throwException = throwException;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 是否为短路模式
     */
    public boolean isShortCircuit() {
        return mockType == MockType.SHORT_CIRCUIT;
    }

    /**
     * 是否为篡改模式
     */
    public boolean isTamper() {
        return mockType == MockType.TAMPER;
    }

    /**
     * 是否有条件规则
     */
    public boolean hasCondition() {
        return conditionRule != null && !conditionRule.isEmpty();
    }

    @Override
    public String toString() {
        return "MockRule{" +
                "methodName='" + methodName + '\'' +
                ", enabled=" + enabled +
                ", mockType=" + mockType +
                ", conditionRule='" + conditionRule + '\'' +
                ", delayMs=" + delayMs +
                ", throwException=" + throwException +
                ", priority=" + priority +
                '}';
    }
}