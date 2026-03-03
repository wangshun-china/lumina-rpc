package com.lumina.rpc.protocol.codec;

import com.lumina.rpc.protocol.RpcMessage;
import com.lumina.rpc.protocol.spi.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 消息编码器
 *
 * 协议格式:
 * +--------+---------+---------------+-------------+-----------+-------------+-----------+
 * | Magic  | Version | Serializer    | MessageType | RequestId | Data Length |   Body    |
 * | 2 bytes| 1 byte  | 1 byte        | 1 byte      | 8 bytes   | 4 bytes     | N bytes   |
 * +--------+---------+---------------+-------------+-----------+-------------+-----------+
 */
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    private static final Logger logger = LoggerFactory.getLogger(RpcEncoder.class);

    private final Serializer serializer;

    public RpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 写入 Magic Number (2 bytes)
            out.writeShort(msg.getMagicNumber());

            // 2. 写入 Version (1 byte)
            out.writeByte(msg.getVersion());

            // 3. 写入 Serializer Type (1 byte)
            out.writeByte(msg.getSerializerType());

            // 4. 写入 Message Type (1 byte)
            out.writeByte(msg.getMessageType());

            // 5. 写入 Request ID (8 bytes)
            out.writeLong(msg.getRequestId());

            // 6. 序列化 Body 并计算长度
            byte[] bodyBytes = null;
            int dataLength = 0;
            if (msg.getBody() != null) {
                bodyBytes = serializer.serialize(msg.getBody());
                dataLength = bodyBytes.length;
            }

            // 7. 写入 Data Length (4 bytes)
            out.writeInt(dataLength);

            // 8. 写入 Body
            if (bodyBytes != null) {
                out.writeBytes(bodyBytes);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Encoded RPC message: requestId={}, messageType={}, dataLength={}",
                        msg.getRequestId(), msg.getMessageType(), dataLength);
            }

        } catch (Exception e) {
            logger.error("Failed to encode RPC message", e);
            throw e;
        }
    }
}