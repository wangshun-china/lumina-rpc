package com.lumina.rpc.protocol;

/**
 * RPC 消息基类，定义消息头结构
 *
 * 协议格式:
 * Magic Number (2 bytes) + Version (1 byte) + Serializer Type (1 byte) +
 * Message Type (1 byte) + Request ID (8 bytes) + Data Length (4 bytes) + Body
 */
public class RpcMessage {

    // Magic Number: 0x4C55 ("LU" in ASCII)
    public static final short MAGIC_NUMBER = (short) 0x4C55;

    // 协议版本
    public static final byte VERSION = 1;

    // 消息类型
    public static final byte REQUEST = 0;
    public static final byte RESPONSE = 1;
    public static final byte HEARTBEAT = 2;

    // 序列化类型
    public static final byte JSON = 0;
    public static final byte KRYO = 1;
    public static final byte PROTOSTUFF = 2;

    // 消息头字段
    private short magicNumber;
    private byte version;
    private byte serializerType;
    private byte messageType;
    private long requestId;
    private int dataLength;

    // 消息体
    private Object body;

    public RpcMessage() {
    }

    public short getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(short magicNumber) {
        this.magicNumber = magicNumber;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getSerializerType() {
        return serializerType;
    }

    public void setSerializerType(byte serializerType) {
        this.serializerType = serializerType;
    }

    public byte getMessageType() {
        return messageType;
    }

    public void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getDataLength() {
        return dataLength;
    }

    public void setDataLength(int dataLength) {
        this.dataLength = dataLength;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public boolean isRequest() {
        return messageType == REQUEST;
    }

    public boolean isResponse() {
        return messageType == RESPONSE;
    }
}