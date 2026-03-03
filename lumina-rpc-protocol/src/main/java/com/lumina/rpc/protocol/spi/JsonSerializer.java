package com.lumina.rpc.protocol.spi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.lumina.rpc.protocol.RpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 容错 JSON 序列化器
 *
 * 支持两种模式：
 * 1. 标准模式（带类型信息）：Consumer 侧使用，能精准还原 DTO 对象
 * 2. 降级模式（无类型信息）：Control Plane 使用，将未知类型转为 LinkedHashMap
 *
 * 当反序列化遇到 InvalidTypeIdException（缺少类定义）时，自动降级处理
 */
public class JsonSerializer implements Serializer {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerializer.class);

    // 标准 ObjectMapper（带多态类型处理）
    private final ObjectMapper typedMapper;

    // 降级 ObjectMapper（无类型处理，用于 Control Plane 等缺少类定义的场景）
    private final ObjectMapper plainMapper;

    public JsonSerializer() {
        // 1. 创建标准 ObjectMapper（带类型信息）
        this.typedMapper = new ObjectMapper();
        this.typedMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.typedMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 开启多态类型处理，JSON 中包含 @class 属性
        this.typedMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 2. 创建降级 ObjectMapper（无类型处理）
        this.plainMapper = new ObjectMapper();
        this.plainMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.plainMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 不开启类型处理，直接反序列化为 Map
        this.plainMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
    }

    /**
     * 获取标准 ObjectMapper 实例（供外部类型转换使用）
     */
    public ObjectMapper getObjectMapper() {
        return typedMapper;
    }

    /**
     * 获取降级 ObjectMapper 实例（供 Control Plane 使用）
     */
    public ObjectMapper getPlainMapper() {
        return plainMapper;
    }

    @Override
    public byte[] serialize(Object obj) {
        try {
            // 序列化时使用带类型信息的 Mapper，确保类型信息被保留
            return typedMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            // 第一次尝试：使用带类型信息的 Mapper
            return typedMapper.readValue(bytes, clazz);

        } catch (InvalidTypeIdException e) {
            // 捕获 InvalidTypeIdException：说明当前进程缺少对应的 Class 定义
            // 这通常发生在 Control Plane 侧（没有业务 DTO 类）
            logger.warn("⚠️ [Fallback] Class not found: {}, falling back to plain deserialization", e.getTypeId());
            logger.debug("InvalidTypeIdException details", e);

            try {
                // 降级处理：使用无类型信息的 Mapper
                // 将数据反序列化为目标类型，但内部嵌套对象会变成 LinkedHashMap
                return plainMapper.readValue(bytes, clazz);

            } catch (JsonProcessingException fallbackEx) {
                logger.error("Failed to deserialize bytes to {} (fallback also failed)", clazz.getName(), fallbackEx);
                throw new RuntimeException("Deserialization failed (fallback)", fallbackEx);
            } catch (IOException fallbackEx) {
                throw new RuntimeException("Deserialization failed (fallback)", fallbackEx);
            }

        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize bytes to {}", clazz.getName(), e);
            throw new RuntimeException("Deserialization failed", e);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    /**
     * 安全反序列化：始终返回有效结果
     *
     * 如果目标类型反序列化失败，返回一个 JsonNode 或 Map
     *
     * @param bytes 字节数组
     * @return 反序列化结果（可能是目标类型，也可能是 JsonNode）
     */
    public Object safeDeserialize(byte[] bytes) {
        try {
            return typedMapper.readValue(bytes, Object.class);
        } catch (InvalidTypeIdException e) {
            logger.debug("Safe deserialize fallback for missing class: {}", e.getTypeId());
            try {
                return plainMapper.readValue(bytes, Object.class);
            } catch (Exception fallbackEx) {
                logger.error("Safe deserialize failed", fallbackEx);
                return null;
            }
        } catch (Exception e) {
            logger.error("Safe deserialize failed", e);
            return null;
        }
    }

    /**
     * 将对象转换为 JSON 字符串（用于日志和调试）
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public String toJsonString(Object obj) {
        try {
            // 使用 plainMapper 避免 @class 信息出现在输出中
            return plainMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert object to JSON string", e);
            return obj != null ? obj.toString() : "null";
        }
    }

    /**
     * 从 JSON 字符串解析为 JsonNode
     *
     * @param json JSON 字符串
     * @return JsonNode
     */
    public JsonNode parseJson(String json) {
        try {
            return plainMapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON", e);
            return plainMapper.getNodeFactory().nullNode();
        }
    }

    @Override
    public byte getType() {
        return RpcMessage.JSON;
    }

    @Override
    public String getName() {
        return "json";
    }
}