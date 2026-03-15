package com.lumina.rpc.protocol.spi;

import com.lumina.rpc.protocol.RpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器管理器
 */
public class SerializerManager {

    private static final Logger logger = LoggerFactory.getLogger(SerializerManager.class);

    // 存储所有已注册的序列化器
    private static final ConcurrentHashMap<Byte, Serializer> SERIALIZERS = new ConcurrentHashMap<>();

    // 默认序列化器类型
    private static volatile byte defaultSerializerType = RpcMessage.JSON;

    static {
        // 加载 SPI 实现的序列化器
        loadSerializers();
        // 注册默认的 JSON 序列化器
        registerSerializer(new JsonSerializer());
        // 注册 KRYO 序列化器（高性能）
        registerSerializer(new KryoSerializer());
    }

    /**
     * 通过 SPI 加载序列化器
     */
    private static void loadSerializers() {
        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : loader) {
            registerSerializer(serializer);
            logger.info("Loaded serializer via SPI: {} (type={})",
                    serializer.getName(), serializer.getType());
        }
    }

    /**
     * 注册序列化器
     *
     * @param serializer 序列化器
     */
    public static void registerSerializer(Serializer serializer) {
        if (serializer == null) {
            return;
        }
        SERIALIZERS.put(serializer.getType(), serializer);
        logger.debug("Registered serializer: {} (type={})",
                serializer.getName(), serializer.getType());
    }

    /**
     * 获取序列化器
     *
     * @param type 序列化类型
     * @return 序列化器
     */
    public static Serializer getSerializer(byte type) {
        Serializer serializer = SERIALIZERS.get(type);
        if (serializer == null) {
            logger.warn("Serializer not found for type: {}, using default JSON", type);
            return SERIALIZERS.get(RpcMessage.JSON);
        }
        return serializer;
    }

    /**
     * 获取默认序列化器
     *
     * @return 默认序列化器
     */
    public static Serializer getDefaultSerializer() {
        return getSerializer(defaultSerializerType);
    }

    /**
     * 设置默认序列化器类型
     *
     * @param type 序列化类型
     */
    public static void setDefaultSerializerType(byte type) {
        if (SERIALIZERS.containsKey(type)) {
            defaultSerializerType = type;
            logger.info("Default serializer type set to: {}", type);
        } else {
            logger.warn("Cannot set default serializer type: {} not registered", type);
        }
    }
}