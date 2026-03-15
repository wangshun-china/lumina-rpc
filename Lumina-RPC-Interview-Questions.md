
# Lumina-RPC 面试题整理 (100道)

> 适用于 Lumina-RPC 企业级 RPC 框架面试准备

---

## 一、Netty 核心基础

### 1. Netty 是什么？它相比原生 NIO 有什么优势？

Netty 是一个基于 NIO 的高性能网络通信框架。优势包括：
- **简化 API**：封装了原生 NIO 复杂的 `Selector`、`Channel`、`Buffer` 操作
- **零拷贝**：支持 `CompositeByteBuf`，减少内存复制
- **ByteBuf 优化**：相比 `ByteBuffer` 更易用，支持动态扩展
- **线程模型**：提供 boss/worker 线程组，简化多线程编程
- **编解码器**：内置多种编解码器，解决 TCP 粘包/半包问题
- **安全性**：完善的心跳检测、断线重连机制

---

### 2. 解释 Netty 的线程模型（Boss Group 和 Worker Group）？

```
┌─────────────────────────────────────────────────────────────┐
│                      Netty 线程模型                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Boss Group (Accept 线程)     Worker Group (I/O 线程)      │
│   ┌─────────────┐              ┌──────────────────┐       │
│   │ Boss Event  │              │ Worker Event     │       │
│   │   Loop      │              │    Loop 1        │       │
│   └──────┬──────┘              └────────┬─────────┘       │
│          │                                │                 │
│          ▼                                ▼                 │
│   ┌─────────────┐              ┌──────────────────┐       │
│   │   接受连接   │────────────▶│  处理读写/业务逻辑 │       │
│   │  ServerSocket│              │    Channel       │       │
│   └─────────────┘              └──────────────────┘       │
│                                                             │
│   职责:                            职责:                    │
│   - 接受客户端连接                  - 读取数据              │
│   - 将连接分配给 Worker             - 解码/编码              │
│   - 绑定端口                        - 执行业务逻辑            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

- **Boss Group**：负责accept连接，通常1个线程
- **Worker Group**：负责处理I/O操作，线程数可配置

---

### 3. Netty 的 `Channel` 和 `ChannelPipeline` 是什么？它们之间的关系是什么？

- **Channel**：代表一个网络连接通道，是 NIO 中的 `SelectableChannel` 封装
- **ChannelPipeline**：是一个 Handler 链，包含了多个 ChannelHandler

关系：
```
Channel 与 ChannelPipeline 一一对应
Pipeline 中的 Handler 按顺序执行，形成责任链模式

入站数据流: Channel → Pipeline(Head) → Handler1 → Handler2 → ... → Pipeline(Tail)
出站数据流: Pipeline(Tail) ← HandlerN ← ... ← Handler1 ← Pipeline(Head) ← Channel
```

---

### 4. 什么是 TCP 粘包和半包问题？Netty 如何解决？

**问题定义**：
- **粘包**：多个小包合并成一个数据包发送
- **半包**：一个数据包被拆分成多个小包接收

```
粘包:
发送: [Hello][World] → 接收: [HelloWorld]

半包:
发送: [HelloWorld] → 接收: [Hel][loWorld]
```

**解决方案**：
项目中使用 `LengthFieldBasedFrameDecoder`：
```java
// 协议格式: Magic(2) + Version(1) + Serializer(1) + Type(1) + RequestId(8) + Length(4) + Body
pipeline.addLast(new LengthFieldBasedFrameDecoder(
    1024 * 1024,  // 最大帧长度
    14,           // 长度字段偏移量 (2+1+1+1+8+1=14)
    4,            // 长度字段长度
    -18,          // 长度字段修正值
    0             // 初始字节偏移
));
```

---

### 5. `LengthFieldBasedFrameDecoder` 的工作原理是什么？项目中是如何配置的？

**工作原理**：
1. 读取字节流，根据长度字段获取消息长度
2. 判断是否累积了完整数据包
3. 完整则提取出来交给下游 Handler

**项目配置**：
```java
// 协议头结构:
// | Magic(2) | Version(1) | Serializer(1) | Type(1) | RequestId(8) | Length(4) | Body |
//                      ▲                                                            ▲
//                   offset=14                                                    lengthFieldOffset=4

pipeline.addLast(new RpcDecoder());
// RpcDecoder 内部使用 LengthFieldBasedFrameDecoder
```

---

### 6. 解释 Netty 中的 `ByteBuf`，它相比 `ByteBuffer` 有什么优势？

| 特性 | ByteBuf | ByteBuffer |
|------|---------|------------|
| 内存分配 | 池化 (Pooled) / 非池化 | 非池化 |
| 读写指针 | 分离 (readerIndex, writerIndex) | 同一指针 |
| 容量 | 可动态扩展 | 固定长度 |
| API | 丰富 (read/write/set/get) | 较少 |
| 零拷贝 | 支持 CompositeByteBuf | 不支持 |
| 安全保障 | 自动边界检查 | 需手动处理 |

---

### 7. Netty 中如何实现心跳检测？项目中使用的是哪种方式？

项目中使用了 `IdleStateHandler`：
```java
// 服务端: 60秒读超时，30秒写超时
pipeline.addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

// 客户端: 30秒写超时 (发送心跳)
pipeline.addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
```

当触发 Idle 状态时，会传递 `IdleStateEvent` 给后续 Handler。

---

### 8. 解释 Netty 的零拷贝（Zero-Copy）机制？

- **Direct Buffer**：使用堆外内存，避免 JVM 堆和系统内存之间的复制
- **CompositeByteBuf**：合并多个 ByteBuf，无需复制
- **FileChannel.transferTo**：操作系统级别的零拷贝
- **Wrapped ByteBuf**：包装已有字节数组，不复制

---

### 9. Netty 的 `EventLoop` 和 `EventLoopGroup` 是什么关系？

```
EventLoopGroup (接口)
    │
    ├── NioEventLoopGroup (实现)
    │       │
    │       └── NioEventLoop[] (多个 EventLoop)
    │              │
    │              └── 每个 EventLoop 包含:
    │                  - Selector (多路复用器)
    │                  - TaskQueue (执行任务队列)
    │                  - ScheduledTaskQueue (定时任务)
    │
    └── 关系: EventLoopGroup 管理多个 EventLoop
             每个 EventLoop 可处理多个 Channel
```

---

### 10. Netty 中的 `ChannelFuture` 是什么？如何处理异步操作？

`ChannelFuture` 代表一个异步 I/O 操作的结果：
```java
// 非阻塞方式
channel.connect(address).addListener((ChannelFutureListener) future -> {
    if (future.isSuccess()) {
        // 连接成功
    } else {
        // 连接失败
    }
});

// 阻塞方式 (项目中较少使用)
channel.writeAndFlush(message).sync();
```

---

### 11. 项目中 Netty Server 绑定地址为什么使用 `0.0.0.0`？

```java
// NettyServer.java:130
ChannelFuture future = serverBootstrap.bind(new InetSocketAddress("0.0.0.0", port)).sync();
```

**原因**：
- `0.0.0.0` 表示监听所有网络接口（127.0.0.1、局域网IP、Docker 容器IP）
- 确保容器内外都能访问服务
- 适配多网卡环境

---

### 12. 解释 Netty 的 `ChannelOption` 配置项？

```java
.channel(NioServerSocketChannel.class)
.option(ChannelOption.SO_BACKLOG, 128)        // 连接队列长度
.option(ChannelOption.SO_REUSEADDR, true)    // 端口复用
.childOption(ChannelOption.SO_KEEPALIVE, true)   // TCP保活
.childOption(ChannelOption.TCP_NODELAY, true)    // 禁用Nagle算法
```

- **SO_BACKLOG**：已完成连接队列长度
- **SO_REUSEADDR**：TIME_WAIT 状态下可重用端口
- **SO_KEEPALIVE**：检测连接是否存活
- **TCP_NODELAY**：禁用 Nagle 算法，降低延迟

---

### 13. Netty 如何实现优雅停机？项目中的实现逻辑是什么？

```java
@PreDestroy
public void shutdown() {
    if (!shutdown.compareAndSet(false, true)) return;

    // 1. 标记为非运行状态
    this.running = false;

    // 2. 优雅关闭 WorkerGroup
    Future<?> workerFuture = workerGroup.shutdownGracefully(
        GRACEFUL_SHUTDOWN_QUIET_PERIOD,  // 3秒
        GRACEFUL_SHUTDOWN_TIMEOUT,         // 10秒
        TimeUnit.SECONDS
    );
    workerFuture.await(15, TimeUnit.SECONDS);

    // 3. 优雅关闭 BossGroup
    Future<?> bossFuture = bossGroup.shutdownGracefully(
        GRACEFUL_SHUTDOWN_QUIET_PERIOD,
        GRACEFUL_SHUTDOWN_TIMEOUT,
        TimeUnit.SECONDS
    );
    bossFuture.await(15, TimeUnit.SECONDS);
}
```

核心：先停止接受新连接，等待现有请求处理完成，再关闭线程组。

---

### 14. 什么是 Netty 的 `ChannelInitializer`？它的作用是什么？

用于初始化 Channel 的 Pipeline：
```java
new ChannelInitializer<SocketChannel>() {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
        pipeline.addLast(new RpcDecoder());
        pipeline.addLast(new RpcEncoder(serializer));
        pipeline.addLast(new NettyServerHandler(requestHandler));
    }
}
```

---

### 15. Netty 中处理器链的执行顺序是怎样的？

```
                    ┌──────────────────────────────────────┐
   Inbound (入站)   │            ChannelPipeline          │
   ──────────────▶  │  Head → Decoder → Encoder → Handler │
                    └──────────────────────────────────────┘

   Outbound (出站)  ◀───────────────────────────────────────
                    Channel → Handler → Encoder → Head
```

Inbound: Channel → Pipeline → Handler
Outbound: Handler → Pipeline → Channel

---

## 二、自定义 RPC 协议

### 16. 项目中自定义 RPC 协议的消息格式是什么？各字段的含义是什么？

```
┌─────────────────────────────────────────────────────────────────┐
│                    RPC 消息协议格式 (17字节固定头)                 │
├────────┬─────────┬────────────┬─────────┬──────────┬────────────┤
│ Magic  │ Version │ Serializer │  Type   │ RequestId│   Length  │
│  2B    │   1B    │    1B      │   1B    │   8B     │    4B     │
├────────┴─────────┴────────────┴─────────┴──────────┴────────────┤
│                            Body (变长)                           │
└─────────────────────────────────────────────────────────────────┘
```

| 字段 | 长度 | 说明 |
|------|------|------|
| Magic | 2B | 魔数 0xCAFE，用于识别协议 |
| Version | 1B | 协议版本号 |
| Serializer | 1B | 序列化类型 (0=JSON, 1=Hessian...) |
| Type | 1B | 消息类型 (0=Request, 1=Response) |
| RequestId | 8B | 请求唯一标识 |
| Length | 4B | Body 字节长度 |
| Body | 变长 | 序列化后的请求/响应数据 |

---

### 17. 为什么要设计 Magic Number？它有什么作用？

**作用**：
1. **协议识别**：区分 RPC 消息和其他网络数据
2. **安全性**：过滤非法连接请求
3. **版本区分**：可扩展支持多版本协议

```java
// 项目中定义
public static final short MAGIC = (short) 0xCAFE;

// 解码时检查
if (magic != MAGIC) {
    throw new IllegalArgumentException("Invalid magic number");
}
```

---

### 18. 协议中的 Version 字段有什么用？

- **向后兼容**：新版本协议可以兼容旧版本客户端
- **协议升级**：平滑切换协议版本
- **降级处理**：根据版本选择不同处理逻辑

---

### 19. Serializer Type 和 Message Type 在协议中的位置和作用是什么？

**Serializer Type** (1B)：
- 标识 Body 的序列化方式
- 0=JSON, 1=Hessian, 2=ProtoBuf 等
- 解码时根据此字段选择对应的反序列化器

**Message Type** (1B)：
- 标识消息是请求还是响应
- 0=Request, 1=Response
- 服务端据此决定如何处理

---

### 20. 项目中如何实现 RPC 请求的异步转同步？

使用 `CompletableFuture` + `ConcurrentHashMap`：
```java
// PendingRequestManager.java
private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();

// 发送请求时
public CompletableFuture<RpcResponse> sendRequest(RpcMessage request) {
    CompletableFuture<RpcResponse> future = new CompletableFuture<>();
    pendingRequests.put(request.getRequestId(), future);
    // 发送...
    return future;  // 返回 Future，调用方可同步等待
}

// 收到响应时
public void onResponse(RpcResponse response) {
    CompletableFuture<RpcResponse> future = pendingRequests.remove(response.getRequestId());
    future.complete(response);  // 唤醒等待线程
}

// 调用方
RpcResponse response = sendRequest(request).get(5, TimeUnit.SECONDS);
```

---

### 21. `RpcEncoder` 和 `RpcDecoder` 的实现原理是什么？

**RpcDecoder**:
```java
public class RpcDecoder extends LengthFieldBasedFrameDecoder {
    public RpcDecoder() {
        super(1024 * 1024, 14, 4, -18, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) return null;

        // 读取协议头
        short magic = frame.readShort();
        // ... 读取其他字段

        // 反序列化 Body
        Serializer serializer = SerializerManager.getSerializer(serializerType);
        Object body = serializer.deserialize(byteBuf, messageType == 0 ? RpcRequest.class : RpcResponse.class);

        return RpcMessage.builder()...build();
    }
}
```

**RpcEncoder**: 将 RpcMessage 按协议格式写入 ByteBuf

---

### 22. 为什么需要在协议头中包含 Data Length？

1. **解决粘包/半包**：告诉解码器需要读取多少字节
2. **按需分配内存**：避免一次性读取整个数据流
3. **边界判断**：准确判断数据包完整性

---

### 23. 请求 ID（Request ID）在 RPC 通信中的作用是什么？

- **请求追踪**：关联请求和响应
- **去重**：防止重复请求
- **超时处理**：按 ID 清理超时请求
- **日志追踪**：便于问题排查

---

### 24. 如何保证请求和响应的配对？

通过 `RequestId` 配对：
```
客户端: RequestId=1001 → 发送请求 → 存入 pendingRequests.get(1001)
服务端: 收到请求 → 处理 → 响应 RequestId=1001
客户端: 收到响应 → 根据 RequestId=1001 找到对应的 CompletableFuture → complete
```

---

### 25. 项目中协议设计的考虑点有哪些？

1. **兼容性**：协议头包含 Version
2. **扩展性**：Serializer Type 可扩展
3. **性能**：固定头部长度，定长部分放前面
4. **安全性**：Magic Number 校验
5. **可靠性**：长度字段防止粘包

---

## 三、动态代理

### 26. 什么是动态代理？Java 中实现动态代理的方式有哪些？

**动态代理**：在运行时动态创建代理类和实例，拦截方法调用

**实现方式**：
1. **JDK 动态代理**：基于 `java.lang.reflect.Proxy`，要求实现接口
2. **CGLIB**：基于 ASM 字节码生成，可代理类
3. **ByteBuddy**：更现代的字节码生成框架

项目使用 ByteBuddy，因为它更强大且 API 更友好。

---

### 27. 项目中为什么选择 ByteBuddy 而不使用 JDK 自带的 Proxy？

| 特性 | ByteBuddy | JDK Proxy |
|------|-----------|-----------|
| 代理类 | 无需接口 | 必须有接口 |
| 性能 | 更高 | 较低 |
| 功能 | 强大(方法拦截、字段修改) | 有限 |
| 依赖 | 需要字节码库 | JDK 内置 |
| 灵活性 | 高 | 低 |

ByteBuddy 允许直接继承类进行代理，更灵活。

---

### 28. ByteBuddy 的核心 API 是什么？如何创建一个代理类？

```java
// ByteBuddyInterceptor.java
public class ByteBuddyInterceptor implements MethodInterceptor {
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
        // 1. 获取方法信息
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();

        // 2. 封装 RpcRequest
        RpcRequest request = RpcRequest.builder()
            .className(className)
            .methodName(methodName)
            .paramTypes(paramTypes)
            .params(args)
            .build();

        // 3. 远程调用
        return rpcClient.invoke(request);
    }
}

// 创建代理
Object proxy = new ByteBuddy()
    .subclass(interfaceClass)
    .method(ElementMatchers.any())
    .intercept(MethodDelegation.to(new ByteBuddyInterceptor()))
    .make()
    .load(classLoader)
    .getLoaded()
    .newInstance();
```

---

### 29. `ByteBuddyInterceptor` 在项目中的作用是什么？它如何拦截方法调用？

**作用**：
- 拦截所有接口方法调用
- 将方法调用转换为 RPC 请求
- 发送到远程服务器
- 返回结果给调用方

**拦截流程**：
```
调用接口方法
       ↓
ByteBuddyInterceptor.intercept()
       ↓
封装 RpcRequest (类名、方法名、参数)
       ↓
通过 NettyClient 发送请求
       ↓
等待响应 (CompletableFuture.get)
       ↓
返回结果
```

---

### 30. 动态代理如何获取被调用方法的信息？

```java
public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
    // 方法名
    String methodName = method.getName();  // "sayHello"

    // 参数类型
    Class<?>[] paramTypes = method.getParameterTypes();  // [String.class]

    // 参数值
    Object[] params = args;  // ["World"]

    // 返回类型
    Class<?> returnType = method.getReturnType();  // String.class

    // 所在类
    String className = method.getDeclaringClass().getName();  // "com.demo.HelloService"
}
```

---

### 31. 项目中 `@LuminaReference` 注解是如何与动态代理结合的？

通过 Spring 的 `BeanPostProcessor`：
```java
// LuminaReferenceAnnotationBeanPostProcessor.java
public class LuminaReferenceAnnotationBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 遍历字段
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(LuminaReference.class)) {
                // 创建代理对象
                Object proxy = proxyFactory.createProxy(field.getType());
                // 注入
                field.setAccessible(true);
                field.set(bean, proxy);
            }
        }
        return bean;
    }
}
```

---

### 32. 代理对象是如何将请求发送到远程服务器的？

```
ByteBuddyInterceptor
       ↓
封装 RpcRequest (方法信息)
       ↓
从 LoadBalancer 获取服务实例
       ↓
获取/创建 Netty Channel
       ↓
发送 RpcMessage
       ↓
CompletableFuture 等待响应
       ↓
返回结果
```

---

### 33. 动态代理的性能开销主要在哪里？如何优化？

**开销**：
- 字节码生成
- 反射调用
- 网络通信
- 序列化/反序列化

**优化**：
- 代理对象缓存
- 连接池复用
- 序列化优化 (ProtoBuf)
- 异步调用

---

### 34. 如果方法返回类型是 void，代理如何处理？

```java
public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
    // 封装请求
    RpcRequest request = ...;

    // 发送请求但不等待响应
    rpcClient.sendRequest(request);

    // void 返回 null
    return null;
}
```

---

## 四、SPI 机制

### 35. 什么是 SPI？它与 API 有什么区别？

| 特性 | API | SPI |
|------|-----|-----|
| 方向 | 框架提供接口，应用调用 | 框架提供接口，应用实现 |
| 主导 | 框架 | 应用 |
| 用途 | 框架扩展 | 插件机制 |

**SPI** (Service Provider Interface)：服务提供接口，框架定义接口，由应用实现。

---

### 36. Java SPI 的实现原理是什么？

```
1. 在 META-INF/services/ 下创建文件
   文件名 = 接口全限定名
   文件内容 = 实现类全限定名

2. ServiceLoader 加载
   ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
   for (Serializer s : loader) {
       // 使用实现类
   }

3. 底层原理
   - 读取 META-INF/services/ 文件
   - 通过反射创建实例
   - 延迟加载
```

---

### 37. 项目中 SPI 接口有哪些？请举例说明？

```java
// Serializer 接口 - 序列化
public interface Serializer {
    byte[] serialize(Object obj);
    Object deserialize(byte[] bytes, Class<?> clazz);
}

// LoadBalancer 接口 - 负载均衡
public interface LoadBalancer {
    ServiceInstance select(List<ServiceInstance> instances);
}
```

---

### 38. 项目中 `META-INF/services` 目录的作用是什么？

```
src/main/resources/
└── META-INF/
    └── services/
        ├── com.lumina.rpc.protocol.spi.Serializer    # 序列化器配置
        └── com.lumina.rpc.core.spi.LoadBalancer      # 负载均衡配置
```

**文件内容**：
```
# com.lumina.rpc.protocol.spi.Serializer
com.lumina.rpc.protocol.spi.JsonSerializer
```

---

### 39. 如何自定义实现一个 SPI 接口？项目中是如何配置的？

**步骤**：
1. 创建实现类
2. 在 META-INF/services/ 创建配置文件
3. 写入实现类全限定名

**示例**：
```java
// 实现接口
public class HessianSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) { ... }
    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) { ... }
}

// 配置文件 META-INF/services/com.lumina.rpc.protocol.spi.Serializer
com.lumina.rpc.protocol.spi.HessianSerializer
```

---

### 40. SPI 的缺点是什么？项目中有何改进？

**缺点**：
- 需遍历所有实现，耗时
- 无法指定顺序
- 无法懒加载

**项目改进**：
```java
// SerializerManager - 管理器模式
public class SerializerManager {
    private static final Map<Byte, Serializer> serializers = new ConcurrentHashMap<>();

    static {
        // 加载并缓存
        ServiceLoader.load(Serializer.class).forEach(s ->
            serializers.put(s.getType(), s));
    }

    public static Serializer getSerializer(byte type) {
        return serializers.get(type);
    }
}
```

---

### 41. `SerializerManager` 和 `LoadBalancerManager` 的实现原理是什么？

```java
public class SerializerManager {
    private static final Map<Byte, Serializer> serializers = new ConcurrentHashMap<>();

    static {
        // 一次性加载所有实现
        ServiceLoader.load(Serializer.class).forEach(s -> {
            serializers.put(s.getType(), s);
        });
    }

    public static Serializer getSerializer(byte type) {
        Serializer s = serializers.get(type);
        if (s == null) {
            throw new IllegalArgumentException("Unknown serializer type: " + type);
        }
        return s;
    }
}
```

---

### 42. SPI 加载失败时项目如何处理？

```java
static {
    try {
        ServiceLoader.load(Serializer.class).forEach(s ->
            serializers.put(s.getType(), s));
    } catch (Exception e) {
        // 使用默认实现
        serializers.put((byte)0, new JsonSerializer());
    }
}
```

---

### 43. 为什么序列化器需要通过 SPI 机制来选择？

- **可插拔**：不修改代码更换序列化方式
- **解耦**：框架不依赖具体实现
- **扩展**：第三方可以自定义序列化器

---

### 44. 如何在不修改代码的情况下切换负载均衡策略？

在 `META-INF/services/com.lumina.rpc.core.spi.LoadBalancer` 中修改实现类名：
```
# 轮询
com.lumina.rpc.core.spi.RoundRobinLoadBalancer

# 或改为加权轮询
com.lumina.rpc.core.spi.WeightedRoundRobinLoadBalancer
```

---

## 五、序列化与反序列化

### 45. 常见的序列化协议有哪些？它们各有什么优缺点？

| 协议 | 优点 | 缺点 |
|------|------|------|
| JSON | 可读、跨语言 | 体积大、性能低 |
| Java Serializable | JDK内置、简单 | 体积大、速度慢、不跨语言 |
| Hessian | 跨语言、性能好 | 依赖第三方 |
| ProtoBuf | 性能极高、体积小 | 需要定义IDL、不可读 |
| Kryo | 速度快、体积小 | 跨语言一般 |
| FST | 速度快 | 不太活跃 |

---

### 46. 项目中使用的是什么序列化方式？为什么选择它？

使用 **JSON 序列化**：
```java
public class JsonSerializer implements Serializer {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(Object obj) {
        return mapper.writeValueAsBytes(obj);
    }

    @Override
    public Object deserialize(byte[] bytes, Class<?> clazz) {
        return mapper.readValue(bytes, clazz);
    }
}
```

**原因**：
- JSON 可读性好，调试方便
- 兼容性好，跨语言
- 对于内网 RPC 性能足够
- 易于实现和调试

---

### 47. JSON 序列化相比 Protobuf/HSF 有什么优势和劣势？

**优势**：
- 可读可调试
- 无需预定义 schema
- 库支持丰富
- 跨平台

**劣势**：
- 序列化后体积大
- 序列化/反序列化性能低
- 无类型校验

---

### 48. 序列化时需要注意哪些问题？

- **循环引用**：对象间相互引用会导致无限递归
- **泛型丢失**：泛型信息在运行时丢失
- ** transient 字段**：标记为 transient 的字段不会被序列化
- **版本兼容**：新增字段要保持向后兼容

---

### 49. 为什么建议 RPC 通信中使用二进制序列化而非 JSON？

- **性能**：二进制序列化速度更快
- **体积**：体积更小，网络传输更快
- **类型**：保留更多类型信息

---

### 50. 解释 `Serializable` 接口的 `serialVersionUID` 有什么作用？

用于版本控制：
```java
private static final long serialVersionUID = 1L;
```

- 类修改后，若 UID 相同，可反序列化
- 若 UID 不同，反序列化会失败
- 建议显式声明，避免自动生成导致兼容性问题

---

### 51. 项目中如何处理序列化/反序列化的版本兼容问题？

- 使用 JSON 天然支持新增字段
- 避免删除字段
- 字段变更时保持字段名稳定

---

### 52. 什么是对象图？序列化时如何处理？

对象图指对象间相互引用的关系网：
```
A → B → C
↑_________|
```

**处理方式**：
- JSON 序列化会处理循环引用
- Jackson 使用 `@JsonIgnore` 忽略部分字段

---

## 六、负载均衡

### 53. 常见的负载均衡算法有哪些？

1. **轮询 (Round Robin)**：依次分配
2. **加权轮询 (Weighted)**：按权重分配
3. **随机 (Random)**：随机选择
4. **最少连接 (Least Connections)**：选择连接数最少
5. **一致性哈希 (Consistent Hash)**：相同 key 映射到相同节点
6. **源地址哈希 (Source IP Hash)**：根据来源 IP 分配

---

### 54. 项目中负载均衡策略是如何实现的？

```java
public class RoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public ServiceInstance select(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            throw new NoProviderAvailableException("No available instances");
        }

        int i = Math.abs(index.getAndIncrement());
        return instances.get(i % instances.size());
    }
}
```

---

### 55. 什么是轮询（Round Robin）负载均衡？项目中如何实现？

轮询：按顺序依次选择服务实例，循环往复。

```java
// 原子变量保证线程安全
private final AtomicInteger index = new AtomicInteger(0);

public ServiceInstance select(List<ServiceInstance> instances) {
    int pos = Math.abs(index.getAndIncrement());
    return instances.get(pos % instances.size());
}
```

---

### 56. 负载均衡如何感知服务实例的状态？

```java
// 过滤掉不可用的实例
public ServiceInstance select(List<ServiceInstance> instances) {
    // 只选择 UP 状态的实例
    List<ServiceInstance> available = instances.stream()
        .filter(instance -> "UP".equals(instance.getStatus()))
        .collect(Collectors.toList());

    if (available.isEmpty()) {
        throw new NoProviderAvailableException("No available instances");
    }

    return doSelect(available);
}
```

---

### 57. 如果某个服务实例不可用了，负载均衡器如何处理？

- **健康检查**：定期检查实例状态
- **故障剔除**：从列表中移除不可用实例
- **重试机制**：客户端自动重连其他实例
- **熔断**：快速失败，防止雪崩

---

### 58. 一致性哈希算法的原理是什么？它适用于什么场景？

**原理**：将哈希值映射到环上，相同 key 始终映射到相同节点

```
         节点C
        /    \
   节点A ─── 节点B

请求 key 的哈希值落在哪个区间，就选择对应节点
```

**适用场景**：分布式缓存、Session 保持

---

### 59. 加权轮询和普通轮询的区别是什么？

| 算法 | 说明 |
|------|------|
| 普通轮询 | 每个实例权重相同 |
| 加权轮询 | 根据权重分配，权重高的被选中次数多 |

```java
public ServiceInstance select(List<ServiceInstance> instances) {
    int totalWeight = instances.stream().mapToInt(ServiceInstance::getWeight).sum();
    int random = new Random().nextInt(totalWeight);

    for (ServiceInstance instance : instances) {
        random -= instance.getWeight();
        if (random < 0) return instance;
    }
    return instances.get(0);
}
```

---

### 60. 项目中负载均衡器的 SPI 接口设计是怎样的？

```java
public interface LoadBalancer {
    ServiceInstance select(List<ServiceInstance> instances);
}
```

配置文件：`META-INF/services/com.lumina.rpc.core.spi.LoadBalancer`

---

### 61. 如何实现最少连接数负载均衡？

```java
public class LeastConnectionsLoadBalancer implements LoadBalancer {
    @Override
    public ServiceInstance select(List<ServiceInstance> instances) {
        return instances.stream()
            .min(Comparator.comparingInt(ServiceInstance::getActiveConnections))
            .orElseThrow();
    }
}
```

---

### 62. 负载均衡策略如何选择？需要考虑哪些因素？

- **服务实例状态**：优先选择健康实例
- **响应时间**：可选择响应最快的
- **权重**：根据机器性能分配
- **地域**：优先选择同地域实例

---

## 七、服务注册与发现

### 63. 什么是服务注册与发现？它解决什么问题？

**服务注册**：服务提供者启动时向注册中心注册自己的地址
**服务发现**：服务消费者从注册中心获取提供者地址列表

**解决问题**：
- 服务地址硬编码
- 服务扩缩容时手动修改配置
- 服务故障无法感知

---

### 64. 项目中服务注册的实现逻辑是什么？

```java
// ServiceRegistryClient.java
public void register(ServiceInstance instance) {
    // 发送注册请求到控制面
    HttpClient.post(CONTROL_PLANE_URL + "/register")
        .body(instance)
        .execute();
}

// Spring 启动时
@PostConstruct
public void registerService() {
    ServiceInstance instance = new ServiceInstance();
    instance.setServiceName(serviceName);
    instance.setIp(ip);
    instance.setPort(port);
    instance.setStatus("UP");
    registryClient.register(instance);
}
```

---

### 65. 服务发现是如何获取可用服务实例列表的？

```java
// ServiceDiscoveryClient.java
public List<ServiceInstance> discover(String serviceName) {
    // 从控制面获取服务实例列表
    List<ServiceInstance> instances = HttpClient.get(
        CONTROL_PLANE_URL + "/discovery/" + serviceName
    ).execute();

    // 过滤 UP 状态的实例
    return instances.stream()
        .filter(i -> "UP".equals(i.getStatus()))
        .collect(Collectors.toList());
}
```

---

### 66. 什么是心跳检测？项目中的心跳机制是如何设计的？

**心跳机制**：
1. 服务提供者定期向控制面发送心跳
2. 控制面更新实例的最后心跳时间
3. 超时未心跳则标记为 DOWN

```java
// 心跳调度
@Scheduled(fixedRate = 30000)  // 30秒
public void sendHeartbeat() {
    HttpClient.post(CONTROL_PLANE_URL + "/heartbeat")
        .body(serviceInstance)
        .execute();
}
```

---

### 67. 服务实例下线后，客户端如何感知？

- **主动通知**：控制面推送变更给客户端
- **拉取更新**：客户端定期拉取最新列表
- **本地缓存**：结合本地缓存 + 定时更新

---

### 68. 什么是服务续约（Renew）？

服务提供者定期向注册中心发送心跳/续约，告诉注册中心"我还活着"。

---

### 69. 项目中控制面数据库表结构是怎样的？

```sql
-- 服务实例表
CREATE TABLE lumina_service_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL,
    ip VARCHAR(64) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(32) DEFAULT 'UP',
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Mock 规则表
CREATE TABLE lumina_mock_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL,
    method_name VARCHAR(255),
    mock_response_json TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### 70. 服务注册中心CAP定理中选择了什么？为什么？

**CAP 定理**：
- **C**onsistency（一致性）
- **A**vailability（可用性）
- **P**artition tolerance（分区容错性）

**选择 AP**：
- 服务注册中心优先保证可用性
- 允许短暂的不一致（心跳延迟）
- 分区容错是必须的（网络问题不可避免）

---

### 71. 什么是临时节点和永久节点？项目中如何选择？

- **临时节点**：服务下线后自动删除，适合服务实例
- **永久节点**：需手动删除，适合配置数据

项目使用临时节点（服务实例下线即删除）。

---

### 72. 服务发现客户端如何缓存服务列表？缓存过期策略是什么？

```java
// 本地缓存 + 定时刷新
private volatile List<ServiceInstance> cachedInstances;
private long cacheExpireTime;

@Scheduled(fixedRate = 60000)  // 1分钟刷新
public void refreshCache() {
    if (System.currentTimeMillis() > cacheExpireTime) {
        cachedInstances = discoveryClient.discover(serviceName);
        cacheExpireTime = System.currentTimeMillis() + 60000;
    }
}
```

---

## 八、Spring 集成

### 73. 项目中 Spring Boot 的自动装配是如何实现的？

```java
// LuminaRpcAutoConfiguration.java
@Configuration
@EnableConfigurationProperties(LuminaRpcProperties.class)
public class LuminaRpcAutoConfiguration {

    @Bean
    public NettyServer nettyServer(Serializer serializer, RpcRequestHandler handler) {
        return new NettyServer(serializer, handler);
    }

    @Bean
    public NettyClient nettyClient(Serializer serializer) {
        return new NettyClient(serializer);
    }

    @Bean
    public LuminaReferenceAnnotationBeanPostProcessor luminaReferenceProcessor() {
        return new LuminaReferenceAnnotationBeanPostProcessor();
    }
}
```

---

### 74. `LuminaRpcAutoConfiguration` 的作用是什么？

定义 Spring Bean，提供自动配置能力，无需手动创建各种组件。

---

### 75. `@LuminaService` 和 `@LuminaReference` 注解是如何工作的？

**@LuminaService**：
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LuminaService {
    String value() default "";
}

// 处理：LuminaServiceAnnotationBeanPostProcessor
// 扫描 @LuminaService 注解的类，注册到服务注册表
```

**@LuminaReference**：
```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LuminaReference {
    String value() default "";
}

// 处理：LuminaReferenceAnnotationBeanPostProcessor
// 为字段注入代理对象
```

---

### 76. Spring 的 `BeanFactory` 和 `ApplicationContext` 有什么区别？

| 特性 | BeanFactory | ApplicationContext |
|------|-------------|-------------------|
| 初始化 | 懒加载 | 预加载 |
| 功能 | 基本 | 增强（事件、国际化等） |
| 注解支持 | 需要配置 | 自动支持 |
| AOP | 需手动配置 | 自动支持 |

---

### 77. 项目中如何将 Netty Server 注册为 Spring Bean？

```java
@Configuration
public class LuminaRpcAutoConfiguration {

    @Bean
    public NettyServer nettyServer(...) {
        NettyServer server = new NettyServer(...);
        // 异步启动
        server.startAsync(port);
        return server;
    }
}
```

---

### 78. `@PreDestroy` 注解的作用是什么？项目中如何使用？

```java
@PreDestroy
public void shutdown() {
    // Spring 容器关闭时执行
    // 优雅停机
}
```

用于资源清理，确保服务停止时释放 Netty 线程组等资源。

---

### 79. Spring 生命周期中的 `InitializationBean` 和 `DisposableBean` 是什么？

- **InitializationBean**：初始化时执行 `afterPropertiesSet()`
- **DisposableBean**：销毁时执行 `destroy()`

```java
public class MyService implements InitializingBean, DisposableBean {
    @Override
    public void afterPropertiesSet() { }  // 初始化

    @Override
    public void destroy() { }  // 销毁
}
```

---

### 80. 项目中如何处理 Spring 容器启动和关闭顺序？

- 使用 `@Order` 注解控制加载顺序
- `@DependsOn` 显式指定依赖
- `@PostConstruct` 和 `@PreDestroy` 处理生命周期

---

### 81. 解释 Spring 中的 `FactoryBean`？项目中有没有使用？

`FactoryBean` 是创建 Bean 的工厂：
```java
public interface FactoryBean<T> {
    T getObject();
    Class<?> getObjectType();
    boolean isSingleton();
}
```

项目当前未使用 FactoryBean。

---

## 九、Mock 降级

### 82. 什么是熔断和降级？它们有什么区别？

| 概念 | 说明 |
|------|------|
| 熔断 | 快速失败，防止故障扩散 |
| 降级 | 返回备选结果，保证服务可用 |

**区别**：
- 熔断：针对故障源，直接拒绝
- 降级：返回兜底数据

---

### 83. 项目中 Mock 降级的实现原理是什么？

```java
// MockRuleManager
public class MockRuleManager {
    private final ConcurrentHashMap<String, MockRule> rules = new ConcurrentHashMap<>();

    public Object getMockResult(RpcRequest request) {
        String key = request.getClassName() + "." + request.getMethodName();
        MockRule rule = rules.get(key);

        if (rule != null && rule.isActive()) {
            return rule.getMockResponse();
        }
        return null;
    }
}
```

---

### 84. `MockRule` 在项目中是如何定义和存储的？

```java
public class MockRule {
    private Long id;
    private String serviceName;
    private String methodName;
    private String mockResponseJson;
    private boolean isActive;
}

// 存储在 MySQL lumina_mock_rule 表
```

---

### 85. 客户端如何获取 Mock 规则？项目中采用什么方式？

```java
// MockRuleSubscriptionClient - 订阅模式
public void subscribe() {
    HttpClient.get(CONTROL_PLANE_URL + "/mock/rules")
        .execute()
        .thenAccept(rules -> {
            // 更新本地缓存
            mockRuleManager.updateRules(rules);
        });
}
```

---

### 86. Mock 降级在什么场景下会被触发？

- 服务调用超时
- 服务不可用
- 控制面下发了 Mock 规则
- 服务调用异常

---

### 87. 项目中 Mock 规则的下发机制是怎样的？

```
控制面（Dashboard） → 修改 Mock 规则 → 存入数据库
                              ↓
                        推送通知到客户端
                              ↓
                      客户端更新本地规则
                              ↓
                      下次调用命中 Mock
```

---

### 88. 如何保证 Mock 返回的数据类型与原始方法返回值一致？

```java
public Object getMockResult(RpcRequest request) {
    MockRule rule = getRule(request);

    // 根据方法返回类型反序列化
    Class<?> returnType = method.getReturnType();
    Object result = mapper.readValue(rule.getMockResponseJson(), returnType);

    return result;
}
```

---

### 89. 什么是异步订阅？项目中 `MockRuleSubscriptionClient` 的作用是什么？

```java
// 异步订阅 Mock 规则更新
public class MockRuleSubscriptionClient {
    public void subscribe(String serviceName) {
        // 长连接订阅
        WebSocket client = new WebSocket();
        client.connect(WS_URL + "/mock/subscribe?service=" + serviceName);
        client.onMessage(msg -> {
            // 更新本地规则
            mockRuleManager.updateRules(msg);
        });
    }
}
```

---

### 90. Mock 降级是否会影响正常服务的调用？如何隔离？

- 使用独立线程处理 Mock，不影响主流程
- Mock 超时时间单独配置
- Mock 结果缓存，减少重复计算

---

## 十、架构与设计

### 91. RPC 框架的核心工作流程是什么？

```
┌─────────────────────────────────────────────────────────────────┐
│                      RPC 调用流程                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   消费者 (Consumer)                提供者 (Provider)            │
│   ┌─────────────┐                ┌─────────────┐               │
│   │  1. 发起调用 │                │  6. 执行方法 │               │
│   │  2. 封装请求 │                │  7. 返回结果 │               │
│   │  3. 序列化   │                │  8. 序列化   │               │
│   │  4. 网络发送 │ ──────────────▶│  9. 网络发送 │              │
│   │  5. 反序列化 │ ◀──────────────│             │               │
│   │  10. 返回结果│                │             │               │
│   └─────────────┘                └─────────────┘               │
│                                                                 │
│   关键组件:                                                     │
│   - 动态代理：屏蔽远程调用细节                                    │
│   - 序列化：对象 ↔ 字节                                          │
│   - 网络传输：Netty                                              │
│   - 服务发现：获取服务地址                                        │
│   - 负载均衡：选择服务实例                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 92. 为什么 RPC 框架需要自定义协议而不是直接使用 HTTP？

- **性能**：HTTP 头部开销大，REST 风格不够高效
- **连接复用**：HTTP 1.x 需频繁建连
- **语义匹配**：RPC 只需要请求/响应语义
- **二进制**：自定义协议可使用二进制格式

---

### 93. 项目中数据面（Data Plane）和控制面（Control Plane）的划分？

```
┌─────────────────────────────────────────────────────────────┐
│                      系统架构                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    控制面 (Control Plane)             │   │
│  │  - Spring Boot 应用                                   │   │
│  │  - 服务注册管理                                       │   │
│  │  - Mock 规则下发                                       │   │
│  │  - Dashboard 提供 Web UI                              │   │
│  └─────────────────────────────────────────────────────┘   │
│                         ▲                                    │
│                    HTTP / WebSocket                          │
│                         ▼                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    数据面 (Data Plane)                │   │
│  │  - lumina-rpc-core (SDK)                             │   │
│  │  - Netty 通信                                         │   │
│  │  - 动态代理                                           │   │
│  │  - 负载均衡                                           │   │
│  │  - Mock 降级                                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

### 94. 什么是透明化 RPC 调用的"假象"？如何实现？

透明化：让远程调用看起来像本地调用

**实现**：
- 动态代理：拦截方法调用
- 注解驱动：`@LuminaReference` 注入代理对象
- 同步化：异步转同步

```java
@LuminaReference
private HelloService helloService;

// 调用时像本地方法一样
String result = helloService.sayHello("World");  // 实际是远程调用
```

---

### 95. 项目中为什么不包含 API Gateway？

根据设计原则：
> "核心原则：纯粹的内部 RPC 与服务治理，绝对不包含对外的 API 网关（Gateway）业务。外部流量由业务层的 Consumer (BFF 模式) 自行处理。"

---

### 96. 解释项目的模块划分和依赖关系？

```
lumina-rpc (父工程)
├── lumina-rpc-protocol     # 协议定义
│   ├── 消息格式 (RpcRequest/RpcResponse)
│   ├── 编解码 (RpcEncoder/RpcDecoder)
│   └── 序列化 (JsonSerializer)
├── lumina-rpc-core         # 核心 SDK
│   ├── 动态代理 (ByteBuddy)
│   ├── 服务发现/注册
│   ├── 负载均衡
│   ├── Mock 降级
│   └── Spring 集成
├── lumina-control-plane    # 控制面
├── lumina-sample-xxx       # 示例模块
└── lumina-dashboard        # 前端
```

---

### 97. 什么是服务治理？项目涉及哪些服务治理能力？

**服务治理**：对微服务的生命周期管理

**项目能力**：
- 服务注册/发现
- 负载均衡
- 动态降级 (Mock)
- 心跳检测
- 健康检查

---

### 98. 项目未来的规划是什么（如虚拟线程优化）？

代码中的预告：
```java
/**
 * 💡 [虚拟线程预告] 💡
 * ============================================================
 * 此处未来可引入 Java 21 虚拟线程进一步提升高并发吞吐量：
 *
 *   ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
 *
 * 虚拟线程 (Virtual Threads) 优势：
 * - 百万级并发连接，内存占用极低
 * - 无需手动管理线程池
 * - 与现有代码完全兼容
 * ============================================================
 */
```

---

### 99. 如果要实现分布式追踪，需要如何改造项目？

1. **埋点**：在 RpcEncoder/Decoder 中添加 traceId
2. **传递**：请求头中携带 traceId
3. **收集**：将追踪数据发送到 Jaeger/Zipkin
4. **存储**：分析展示

---

### 100. 如何保证 RPC 调用的可靠性？项目中做了哪些防御性编程？

**可靠性措施**：
1. **重连机制**：连接断开自动重连
2. **超时控制**：请求超时自动放弃
3. **优雅停机**：释放资源，不丢失请求
4. **健康检查**：定期检查服务状态
5. **熔断降级**：故障时返回 Mock 数据

**防御性编程**：
```java
// 1. 空指针检查
if (channel != null && channel.isActive()) { }

// 2. 状态检查
if (running) {
    logger.warn("Server is already running");
    return;
}

// 3. 防止重复关闭
if (!shutdown.compareAndSet(false, true)) {
    return;
}

// 4. 异常处理
try {
    // 业务逻辑
} catch (Exception e) {
    logger.error("Error", e);
}
```

---

## 附加：Java 基础与进阶

### 101. Java 21 的虚拟线程（Virtual Threads）是什么？项目中有什么规划？

**虚拟线程**：轻量级线程，由 JVM 管理，底层使用少量系统线程

**优势**：
- 百万级并发，内存占用极低
- 无需线程池管理

**项目规划**：见代码注释，未来可替换 NioEventLoopGroup

---

### 102. `CompletableFuture` 在项目中的使用场景是什么？

异步转同步：
```java
CompletableFuture<RpcResponse> future = new CompletableFuture<>();
pendingRequests.put(requestId, future);
// 发送请求...
return future.get(5, TimeUnit.SECONDS);
```

---

### 103. `ConcurrentHashMap` 的实现原理是什么？项目中如何使用？

- **JDK 7**：Segment 分段锁
- **JDK 8+**：CAS + synchronized + 红黑树

项目中使用：
```java
private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();
```

---

### 104. 什么是反射？它对性能有什么影响？项目中是否使用？

**反射**：在运行时动态获取类信息、调用方法

**性能影响**：
- 比普通调用慢 3-10 倍
- 首次调用需要解析

**项目使用**：BeanPostProcessor 中使用反射注入字段

---

### 105. `volatile` 关键字的作用是什么？项目中哪里用到了？

**作用**：
- 保证可见性
- 禁止指令重排序

**项目中**：
```java
private volatile boolean running = false;
```

---

## 参考答案总结

本面试题涵盖了 Lumina-RPC 项目的核心技术点：

| 类别 | 题目数量 | 核心内容 |
|------|---------|---------|
| Netty 基础 | 15 | 线程模型、Pipeline、心跳、优雅停机 |
| 自定义协议 | 10 | 协议设计、编解码、异步转同步 |
| 动态代理 | 9 | ByteBuddy、拦截机制、Spring集成 |
| SPI 机制 | 10 | 序列化器、负载均衡、插件化 |
| 序列化 | 8 | JSON、Protobuf、兼容性问题 |
| 负载均衡 | 10 | 轮询、加权、一致性哈希 |
| 服务发现 | 10 | 注册、心跳、健康检查 |
| Spring集成 | 9 | 自动装配、生命周期 |
| Mock降级 | 9 | 熔断、降级、规则下发 |
| 架构设计 | 10 | 整体架构、可靠性、优化 |

建议结合项目源码深入理解每个知识点。