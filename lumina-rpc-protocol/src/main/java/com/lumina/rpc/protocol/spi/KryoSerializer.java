package com.lumina.rpc.protocol.spi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * KRYO 序列化器
 *
 * 性能特点：
 * - 序列化速度比 JSON 快 5-10 倍
 * - 序列化后的字节大小约为 JSON 的 1/3
 * - 线程安全，使用对象池优化
 *
 * 使用场景：
 * - 大数据量传输
 * - 高性能场景
 * - 内部服务调用
 *
 * @author Lumina-RPC Team
 * @since 1.1.0
 */
public class KryoSerializer implements Serializer {

    private static final Logger logger = LoggerFactory.getLogger(KryoSerializer.class);

    /** KRYO 类型代码 - 与 RpcMessage.KRYO 保持一致 */
    public static final byte TYPE = 1;

    /** KRYO 对象池 - 避免每次创建新实例 */
    private final Pool<Kryo> kryoPool = new Pool<Kryo>(true, false, 16) {
        @Override
        protected Kryo create() {
            Kryo kryo = new Kryo();
            // 设置为不注册类，支持任意类型序列化
            // 生产环境建议注册类以提升性能
            kryo.setRegistrationRequired(false);
            // 允许循环引用
            kryo.setReferences(true);
            return kryo;
        }
    };

    /** Output 缓冲池 */
    private final Pool<Output> outputPool = new Pool<Output>(true, false, 16) {
        @Override
        protected Output create() {
            return new Output(1024, -1);
        }
    };

    /** Input 缓冲池 */
    private final Pool<Input> inputPool = new Pool<Input>(true, false, 16) {
        @Override
        protected Input create() {
            return new Input(1024);
        }
    };

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }

        Kryo kryo = kryoPool.obtain();
        Output output = outputPool.obtain();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            output.setOutputStream(baos);

            kryo.writeClassAndObject(output, obj);
            output.flush();

            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("KRYO serialize failed for type: {}", obj.getClass().getName(), e);
            throw new RuntimeException("KRYO serialization failed", e);

        } finally {
            outputPool.free(output);
            kryoPool.free(kryo);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Kryo kryo = kryoPool.obtain();
        Input input = inputPool.obtain();

        try {
            input.setInputStream(new ByteArrayInputStream(bytes));
            Object obj = kryo.readClassAndObject(input);

            if (obj != null && !clazz.isInstance(obj)) {
                throw new ClassCastException(
                        "Cannot cast " + obj.getClass().getName() + " to " + clazz.getName());
            }

            return (T) obj;

        } catch (Exception e) {
            logger.error("KRYO deserialize failed for type: {}", clazz.getName(), e);
            throw new RuntimeException("KRYO deserialization failed", e);

        } finally {
            inputPool.free(input);
            kryoPool.free(kryo);
        }
    }

    @Override
    public byte getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return "kryo";
    }
}