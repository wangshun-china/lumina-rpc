package com.lumina.rpc.protocol.codec;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.RpcRequest;
import com.lumina.rpc.protocol.RpcResponse;
import com.lumina.rpc.protocol.spi.Serializer;
import com.lumina.rpc.protocol.spi.SerializerManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 消息解码器
 *
 * 协议格式:
 * +--------+---------+---------------+-------------+-----------+-------------+-----------+
 * | Magic  | Version | Serializer    | MessageType | RequestId | Data Length |   Body    |
 * | 2 bytes| 1 byte  | 1 byte        | 1 byte      | 8 bytes   | 4 bytes     | N bytes   |
 * +--------+---------+---------------+-------------+-----------+-------------+-----------+
 *
 * 使用 LengthFieldBasedFrameDecoder 解决 TCP 粘包/半包问题
 */
public class RpcDecoder extends LengthFieldBasedFrameDecoder {

    private static final Logger logger = LoggerFactory.getLogger(RpcDecoder.class);

    // 协议头长度: Magic(2) + Version(1) + Serializer(1) + MessageType(1) + RequestId(8) + DataLength(4) = 17
    private static final int HEADER_LENGTH = 17;

    public RpcDecoder() {
        // lengthFieldOffset: Data Length 字段的起始位置 (13)
        // lengthFieldLength: Data Length 字段长度 (4 bytes)
        // lengthAdjustment: 长度调整值 (0，因为 Data Length 只包含 body 长度)
        // initialBytesToStrip: 解码后跳过的字节数 (0，保留完整消息)
        super(65536, 13, 4, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 先调用父类方法解决粘包/半包问题
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            return decodeFrame(frame);
        } finally {
            frame.release();
        }
    }

    private Object decodeFrame(ByteBuf in) {
        // 检查数据长度是否足够
        if (in.readableBytes() < HEADER_LENGTH) {
            logger.warn("Invalid message: insufficient header length");
            return null;
        }

        // 1. 读取 Magic Number
        short magic = in.readShort();
        if (magic != RpcMessage.MAGIC_NUMBER) {
            logger.warn("Invalid magic number: {}, expected: {}", magic, RpcMessage.MAGIC_NUMBER);
            return null;
        }

        // 2. 读取 Version
        byte version = in.readByte();
        if (version != RpcMessage.VERSION) {
            logger.warn("Unsupported version: {}, expected: {}", version, RpcMessage.VERSION);
            return null;
        }

        // 3. 读取 Serializer Type
        byte serializerType = in.readByte();

        // 4. 读取 Message Type
        byte messageType = in.readByte();

        // 5. 读取 Request ID
        long requestId = in.readLong();

        // 6. 读取 Data Length
        int dataLength = in.readInt();

        RpcMessage message = new RpcMessage();
        message.setMagicNumber(magic);
        message.setVersion(version);
        message.setSerializerType(serializerType);
        message.setMessageType(messageType);
        message.setRequestId(requestId);
        message.setDataLength(dataLength);

        // 7. 读取 Body
        if (dataLength > 0 && in.readableBytes() >= dataLength) {
            byte[] bodyBytes = new byte[dataLength];
            in.readBytes(bodyBytes);

            // 反序列化 body
            try {
                Serializer serializer = SerializerManager.getSerializer(serializerType);
                Class<?> bodyClass = getBodyClass(messageType);
                Object body = serializer.deserialize(bodyBytes, bodyClass);
                message.setBody(body);
            } catch (Exception e) {
                logger.error("Failed to deserialize message body", e);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Decoded RPC message: requestId={}, messageType={}, dataLength={}",
                    requestId, messageType, dataLength);
        }

        return message;
    }

    private Class<?> getBodyClass(byte messageType) {
        switch (messageType) {
            case RpcMessage.REQUEST:
                return RpcRequest.class;
            case RpcMessage.RESPONSE:
                return RpcResponse.class;
            default:
                return Object.class;
        }
    }
}