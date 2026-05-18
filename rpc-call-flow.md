# Lumina-RPC 调用流程详解

## 一、Spring Bean 创建与代理注入阶段（启动时）

### 1.1 涉及的核心类

| 类名 | 职责 | 来源 |
|------|------|------|
| `EngineServiceClient` | 消费者客户端，持有 `@LuminaReference` 字段 | 业务代码 |
| `LuminaReferenceAnnotationBeanPostProcessor` | 处理 `@LuminaReference` 注解，注入代理 | RPC框架 |
| `ProxyFactory` | 使用 ByteBuddy 创建动态代理 | RPC框架 |
| `RpcClientHandler` | 代理的实际处理器，负责 RPC 调用 | RPC框架 |
| `ByteBuddyInterceptor` | 方法拦截器，将调用转给 RpcClientHandler | RPC框架 |

### 1.2 Spring Bean 生命周期（简化版）

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Spring 容器启动                                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  第1步：扫描 @Component                                            │
│  ─────────────────────                                               │
│  Spring 发现 EngineServiceClient 类上有 @Component 注解            │
│  决定：需要创建这个类的 Bean 实例                                    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  第2步：实例化 Bean（调用构造函数）                                  │
│  ─────────────────────────────────                                   │
│  new EngineServiceClient()                                          │
│                                                                     │
│  注意：此时 engineService 字段的值是 null！                          │
│  ┌─────────────────────────────┐                                   │
│  │ EngineServiceClient@1234    │                                   │
│  │ ─────────────────────────   │                                   │
│  │ engineService = null  ◄── 还没注入                              │
│  └─────────────────────────────┘                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  第3步：依赖注入（构造函数注入）                                      │
│  ───────────────────────────────                                     │
│  Spring 发现构造函数需要 TelemetryService 等参数                    │
│  于是先创建这些依赖的 Bean，然后注入                                 │
│                                                                     │
│  public EngineServiceClient(TelemetryService telemetryService) {    │
│      this.telemetryService = telemetryService;  ◄── 注入完成        │
│  }                                                                  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  第4步：BeanPostProcessor 处理（关键！）                             │
│  ───────────────────────────────────                                 │
│  Spring 调用所有 BeanPostProcessor 的                               │
│  postProcessBeforeInitialization() 方法                             │
│                                                                     │
│  其中 LuminaReferenceAnnotationBeanPostProcessor 发现：              │
│  - EngineServiceClient 有个字段标注了 @LuminaReference              │
│  - 字段类型是 EngineService.class                                   │
│  - 需要注入一个代理对象                                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│  第5步：创建并注入代理                                               │
│  ─────────────────────                                               │
│  详细过程见下文...                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 代理创建与注入的详细过程

当 `LuminaReferenceAnnotationBeanPostProcessor` 发现 `@LuminaReference` 注解时：

```java
// 1. 获取字段类型（接口）
Class<?> interfaceClass = field.getType();  // EngineService.class

// 2. 从注解读取配置
String version = reference.version();       // ""
long timeout = reference.timeout();         // 5000
int retries = reference.retries();          // 3
String cluster = reference.cluster();       // "failover"

// 3. 调用 ProxyFactory 创建代理
Object proxy = proxyFactory.createProxy(
    interfaceClass, version, timeout, async, cluster, retries,
    enableCircuitBreaker, circuitBreakerThreshold, circuitBreakerTimeout,
    enableRateLimit, rateLimitPermits
);

// 4. 反射注入到字段
ReflectionUtils.makeAccessible(field);
field.set(bean, proxy);  // 将代理设置到 engineService 字段
```

**ProxyFactory 内部创建代理的过程：**

```java
public <T> T createProxy(Class<T> interfaceClass, ...) {
    // 1. 创建 RpcClientHandler（真正处理 RPC 调用的类）
    RpcClientHandler clientHandler = new RpcClientHandler(
        interfaceClass, version, timeout, async, cluster, retries, nettyClient, ...
    );

    // 2. 使用 ByteBuddy 创建代理类
    Class<T> proxyClass = (Class<T>) new ByteBuddy()
        .subclass(Object.class)                           // 继承 Object
        .implement(interfaceClass)                        // 实现 EngineService 接口
        .method(ElementMatchers.isDeclaredBy(interfaceClass))  // 匹配接口所有方法
        .intercept(MethodDelegation.to(new ByteBuddyInterceptor(clientHandler)))  // 拦截方法
        .make()
        .load(interfaceClass.getClassLoader())
        .getLoaded();

    // 3. 实例化代理对象
    T proxyInstance = proxyClass.getDeclaredConstructor().newInstance();

    return proxyInstance;
}
```

**最终注入完成后的对象结构：**

```
EngineServiceClient@1234 (Spring Bean)
├── telemetryService = TelemetryService@5678     (构造函数注入)
├── engineService = EngineService$ByteBuddy$xxx  (@LuminaReference 注入的代理)
│   └── 代理内部持有 ByteBuddyInterceptor
│       └── 内部持有 RpcClientHandler
│           ├── interfaceClass = EngineService.class
│           ├── timeout = 5000
│           ├── cluster = "failover"
│           ├── retries = 3
│           └── nettyClient = NettyClient@9999
```

---

## 二、HTTP 请求处理阶段（运行时）

### 2.1 请求入口

```
用户发起 HTTP 请求:
GET http://localhost:8083/api/command/jump/NCC-1701/Alpha-7

Spring 路由到 CommandController.executeJump("NCC-1701", "Alpha-7")
```

### 2.2 Controller 内部流程

```java
@GetMapping("/jump/{shipId}/{sector}")
public ResponseEntity<?> executeJump(@PathVariable String shipId, @PathVariable String sector) {
    // shipId = "NCC-1701"
    // sector = "Alpha-7"

    // 并发发起两个 RPC 调用
    CompletableFuture<EngineService.WarpStatusDTO> engineFuture =
        CompletableFuture.supplyAsync(() ->
            engineServiceClient.getEngineStatus(shipId)  // ◄── 进入 EngineServiceClient
        );

    CompletableFuture<RadarService.ScanResult> radarFuture =
        CompletableFuture.supplyAsync(() ->
            radarServiceClient.scanEnemies(sector)
        );
    ...
}
```

### 2.3 EngineServiceClient 调用代理

```java
// EngineServiceClient.java
public EngineService.WarpStatusDTO getEngineStatus(String shipId) {
    // 这里的 engineService 是 ByteBuddy 创建的代理对象！
    return engineService.getWarpStatus(shipId);  // ◄── 调用代理方法
}
```

当调用 `engineService.getWarpStatus(shipId)` 时，代理会拦截这个调用。

---

## 三、代理拦截阶段（运行时）

### 3.1 ByteBuddy 代理拦截方法调用

ByteBuddy 生成的代理类伪代码（简化）：

```java
// 这是 ByteBuddy 动态生成的类，类似这样：
public class EngineService$ByteBuddy$abc123 implements EngineService {

    private ByteBuddyInterceptor interceptor;  // 构造时传入

    @Override
    public WarpStatusDTO getWarpStatus(String shipId) {
        // 所有方法调用都被拦截，转给 ByteBuddyInterceptor
        return interceptor.intercept(
            this,                    // 代理对象本身
            EngineService.class.getMethod("getWarpStatus", String.class),  // 方法
            new Object[]{shipId}     // 参数
        );
    }
}
```

### 3.2 ByteBuddyInterceptor 转发给 RpcClientHandler

```java
public class ByteBuddyInterceptor {
    private final RpcClientHandler clientHandler;

    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws Throwable {
        // 将调用转发给 RpcClientHandler
        return clientHandler.invoke(null, method, args);
    }
}
```

### 3.3 RpcClientHandler 处理 RPC 调用

```java
public class RpcClientHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String serviceName = interfaceClass.getName();  // "com.lumina.sample.engine.service.EngineService"
        String methodName = method.getName();           // "getWarpStatus"

        // 1. 检查 Mock 规则（短路拦截）
        MockRule matchedRule = mockRuleManager.getMatchingRule(serviceName, methodName, args);
        if (matchedRule != null && matchedRule.isShortCircuit()) {
            return mockRuleManager.executeMock(serviceName, methodName, args, method.getReturnType());
        }

        // 2. 构建 RpcRequest
        RpcRequest request = buildRpcRequest(method, args);
        // request 包含：requestId, interfaceName, methodName, parameterTypes, parameters, traceId

        // 3. 发送请求（走集群容错策略）
        return sendRequest(request, method);
    }
}
```

---

## 四、服务发现与负载均衡阶段

### 4.1 sendRequest 方法流程

```java
private Object sendRequest(RpcRequest request, Method method) throws Throwable {
    String serviceName = request.getInterfaceName();

    // 步骤1: 服务发现
    List<ServiceInstance> instances = ServiceDiscovery.getServiceInstances(serviceName, version);
    // 返回：[EngineService@192.168.1.100:8081, EngineService@192.168.1.101:8081]

    if (instances.isEmpty()) {
        throw new NoProviderAvailableException(serviceName);
    }

    // 步骤2: 获取动态配置（超时、重试、集群策略）
    ProtectionConfig config = getProtectionConfig(serviceName);
    long effectiveTimeout = config != null ? config.getTimeout() : this.timeout;
    int effectiveRetries = config != null ? config.getRetries() : this.retries;
    String effectiveCluster = config != null ? config.getClusterStrategy() : this.cluster;

    // 步骤3: 集群容错调用
    Cluster clusterStrategy = ClusterManager.getInstance().getCluster(effectiveCluster);
    // 获取 FailoverCluster 实例

    ClusterInvocation invocation = new ClusterInvocation(
        serviceName, version, request, method.getReturnType(),
        instances, loadBalancer, nettyClient, effectiveTimeout, effectiveRetries,
        enableCircuitBreaker, circuitBreakerThreshold, circuitBreakerTimeout,
        enableRateLimit, rateLimitPermits
    );

    Object result = clusterStrategy.invoke(invocation);

    return result;
}
```

---

## 五、集群容错阶段（以 FailoverCluster 为例）

### 5.1 FailoverCluster 执行流程

```java
public class FailoverCluster implements Cluster {

    @Override
    public Object invoke(ClusterInvocation invocation) throws Throwable {
        // 获取重试次数
        int retries = invocation.getRetries();  // 默认 3
        int attempts = 0;

        while (attempts <= retries) {
            attempts++;

            // 1. 限流检查
            if (invocation.isEnableRateLimit()) {
                if (!rateLimiter.tryAcquire()) {
                    throw new RateLimitException("Rate limit exceeded");
                }
            }

            // 2. 熔断检查
            CircuitBreaker cb = getCircuitBreaker(invocation);
            if (!cb.allowRequest()) {
                throw new CircuitBreakerOpenException("Circuit breaker is open");
            }

            try {
                // 3. 执行实际调用
                Object result = doInvokeWithRetry(invocation);

                // 4. 成功，记录成功
                cb.recordSuccess();
                return result;

            } catch (Exception e) {
                // 5. 失败，记录失败
                cb.recordFailure();

                // 6. 判断是否还有重试次数
                if (attempts > retries) {
                    throw e;  // 重试耗尽，抛出异常
                }

                // 7. 等待后重试（退避策略）
                long backoff = calculateBackoff(attempts);
                Thread.sleep(backoff);
            }
        }

        throw new RpcException("Failover retries exhausted");
    }
}
```

---

## 六、网络传输阶段（Netty）

### 6.1 RpcInvoker 发起实际网络调用

```java
public class RpcInvoker {

    public static CompletableFuture<Object> invokeAsync(
            InetSocketAddress address,
            RpcRequest request,
            NettyClient nettyClient,
            long timeout) {

        // 1. 创建 RpcMessage（协议层消息）
        RpcMessage message = new RpcMessage();
        message.setMessageType(RpcMessage.REQUEST);
        message.setSerializerType(SerializerManager.getDefaultSerializer().getType());
        message.setBody(request);

        // 2. 生成请求ID，注册到 PendingRequestManager
        long requestId = request.getRequestId();
        CompletableFuture<Object> future = new CompletableFuture<>();
        PendingRequestManager.register(requestId, future);

        // 3. 通过 NettyClient 发送消息
        Channel channel = nettyClient.getChannel(address);
        channel.writeAndFlush(message);

        // 4. 设置超时
        future.orTimeout(timeout, TimeUnit.MILLISECONDS);

        return future;
    }
}
```

### 6.2 协议编码（RpcEncoder）

```java
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        // 1. 写入魔数 (4 bytes)
        out.writeBytes(RpcMessage.MAGIC);

        // 2. 写入版本号 (1 byte)
        out.writeByte(RpcMessage.VERSION);

        // 3. 写入序列化类型 (1 byte)
        out.writeByte(msg.getSerializerType());

        // 4. 写入消息类型 (1 byte)
        out.writeByte(msg.getMessageType());

        // 5. 写入请求ID (8 bytes)
        out.writeLong(msg.getRequestId());

        // 6. 序列化 body
        byte serializerType = msg.getSerializerType();
        Serializer serializer = SerializerManager.getSerializer(serializerType);
        byte[] bodyBytes = serializer.serialize(msg.getBody());

        // 7. 写入 body 长度 (4 bytes)
        out.writeInt(bodyBytes.length);

        // 8. 写入 body
        out.writeBytes(bodyBytes);

        // 总消息头长度：4 + 1 + 1 + 1 + 8 + 4 = 19 字节
    }
}
```

### 6.3 网络传输

```
┌─────────────────┐                     ┌─────────────────┐
│   Command服务   │      TCP 网络        │   Engine服务    │
│   (消费者)      │  ═════════════════► │   (提供者)      │
│                 │   19字节头 + body   │                 │
└─────────────────┘                     └─────────────────┘
       │                                        │
       │ 1. 通过 Socket 发送字节流               │
       │───────────────────────────────────────>│
       │                                        │
       │ 2. Provider 接收字节流                  │
       │                                        │ RpcDecoder 解码
       │ 3. 反序列化得到 RpcRequest              │
       │                                        │
       │ 4. 执行本地方法 getWarpStatus()         │
       │                                        │
       │ 5. 返回 RpcResponse                     │
       │<───────────────────────────────────────│
       │                                        │
       │ 6. Consumer 收到响应                    │
       │   唤醒等待的 CompletableFuture          │
```

---

## 七、Provider 端处理阶段

### 7.1 消息接收与解码

```java
// RpcDecoder 解码字节流
@Override
protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    // 1. 使用 LengthFieldBasedFrameDecoder 解决粘包/半包问题
    ByteBuf frame = (ByteBuf) super.decode(ctx, in);

    // 2. 解析消息头
    byte[] magic = new byte[4];
    frame.readBytes(magic);           // 魔数
    byte version = frame.readByte();  // 版本
    byte serializerType = frame.readByte();  // 序列化类型
    byte messageType = frame.readByte();     // 消息类型
    long requestId = frame.readLong();       // 请求ID
    int dataLength = frame.readInt();        // 数据长度

    // 3. 读取 body
    byte[] data = new byte[dataLength];
    frame.readBytes(data);

    // 4. 根据序列化类型动态选择序列化器
    Serializer serializer = SerializerManager.getSerializer(serializerType);
    Object body = serializer.deserialize(data, getBodyClass(messageType));

    // 5. 构建 RpcMessage
    RpcMessage message = new RpcMessage();
    message.setRequestId(requestId);
    message.setMessageType(messageType);
    message.setSerializerType(serializerType);
    message.setBody(body);

    out.add(message);
}
```

### 7.2 RpcRequestHandler 处理请求

```java
// RpcRequestHandler.java (Netty 的 ChannelInboundHandler)
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof RpcMessage) {
        RpcMessage message = (RpcMessage) msg;

        if (message.getMessageType() == RpcMessage.REQUEST) {
            RpcRequest request = (RpcRequest) message.getBody();

            // 1. 从本地服务缓存找到对应的实现类
            Object serviceInstance = serviceRegistry.getService(
                request.getInterfaceName(),
                request.getVersion()
            );

            // 2. 通过反射调用本地方法
            Method method = serviceInstance.getClass().getMethod(
                request.getMethodName(),
                request.getParameterTypes()
            );
            Object result = method.invoke(serviceInstance, request.getParameters());

            // 3. 构建 RpcResponse
            RpcResponse response = new RpcResponse();
            response.setRequestId(request.getRequestId());
            response.setResult(result);

            // 4. 封装成 RpcMessage 返回
            RpcMessage responseMessage = new RpcMessage();
            responseMessage.setMessageType(RpcMessage.RESPONSE);
            responseMessage.setRequestId(request.getRequestId());
            responseMessage.setBody(response);

            ctx.writeAndFlush(responseMessage);
        }
    }
}
```

---

## 八、响应返回阶段

### 8.1 消费者接收响应

```java
// RpcClientHandler (Netty 的 ChannelInboundHandler)
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof RpcMessage) {
        RpcMessage message = (RpcMessage) msg;

        if (message.getMessageType() == RpcMessage.RESPONSE) {
            RpcResponse response = (RpcResponse) message.getBody();
            long requestId = response.getRequestId();

            // 1. 从 PendingRequestManager 找到对应的 CompletableFuture
            CompletableFuture<Object> future = PendingRequestManager.remove(requestId);

            // 2. 如果有异常，设置异常
            if (response.getException() != null) {
                future.completeExceptionally(response.getException());
            } else {
                // 3. 正常完成，设置结果
                future.complete(response.getResult());
            }
        }
    }
}
```

### 8.2 结果层层返回

```
┌─────────────────────────────────────────────────────────────────────┐
│                     响应返回流程                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Netty 接收响应字节流                                            │
│           ↓                                                         │
│  2. RpcDecoder 解码成 RpcMessage                                    │
│           ↓                                                         │
│  3. RpcClientHandler.channelRead() 找到对应 CompletableFuture      │
│           ↓                                                         │
│  4. future.complete(response.getResult()) 唤醒等待线程             │
│           ↓                                                         │
│  5. FailoverCluster 收到结果，返回                                  │
│           ↓                                                         │
│  6. RpcClientHandler.invoke() 返回                                 │
│           ↓                                                         │
│  7. ByteBuddyInterceptor.intercept() 返回                          │
│           ↓                                                         │
│  8. 代理对象返回结果给 EngineServiceClient                         │
│           ↓                                                         │
│  9. EngineServiceClient.getEngineStatus() 返回                     │
│           ↓                                                         │
│  10. CommandController 收到 WarpStatusDTO                          │
│           ↓                                                         │
│  11. 包装成 JumpResponse 返回给 HTTP 客户端                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 九、完整调用时序图

```
┌──────────┐  ┌─────────────────┐  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
│  HTTP    │  │  CommandController│  │EngineServiceClient│ │ ByteBuddy   │  │ RpcClientHandler│ │FailoverCluster│ │ Netty    │  │  Engine  │
│  Client  │  │                 │  │             │  │   Proxy      │  │               │  │               │  │  Client  │  │  Service │
└────┬─────┘  └───────┬─────────┘  └──────┬──────┘  └──────┬───────┘  └───────┬───────┘  └───────┬───────┘  └────┬─────┘  └────┬─────┘
     │                │                   │                │                  │                  │             │            │
     │ 1. GET /jump   │                   │                │                  │                  │             │            │
     │───────────────>│                   │                │                  │                  │             │            │
     │                │                   │                │                  │                  │             │            │
     │                │ 2. getEngineStatus│                │                  │                  │             │            │
     │                │──────────────────>│                │                  │                  │             │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │ 3. 调用代理方法 │                  │                  │             │            │
     │                │                   │────────────────>│                  │                  │             │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │ 4. intercept()   │                  │             │            │
     │                │                   │                │─────────────────>│                  │             │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │ 5. invoke()      │             │            │
     │                │                   │                │                  │─────────────────>│             │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │                  │ 6. invoke() │            │
     │                │                   │                │                  │                  │────────────>│            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │                  │ 7. 服务发现  │            │
     │                │                   │                │                  │                  │ 8. 负载均衡  │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │                  │ 9. 发送请求  │            │
     │                │                   │                │                  │                  │─────────────┼───────────>│
     │                │                   │                │                  │                  │             │ 网络传输   │
     │                │                   │                │                  │                  │             │───────────>│
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │                  │             │            │ 10. 执行本地方法
     │                │                   │                │                  │                  │             │            │─────┐
     │                │                   │                │                  │                  │             │            │     │
     │                │                   │                │                  │                  │             │            │<────┘
     │                │                   │                │                  │                  │             │ 11. 返回结果│
     │                │                   │                │                  │                  │             │<───────────│
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │ 12. 收到响应      │             │            │
     │                │                   │                │                  │<─────────────────│             │            │
     │                │                   │                │                  │                  │             │            │
     │                │                   │                │                  │ 13. 返回结果      │             │            │
     │                │                   │                │                  │<─────────────────│             │            │
     │                │                   │                │ 14. 返回结果      │                  │             │            │
     │                │                   │                │<─────────────────│                  │             │            │
     │                │                   │ 15. 返回 WarpStatusDTO             │                  │             │            │
     │                │                   │<────────────────                │                  │             │            │
     │                │ 16. 返回结果       │                │                  │                  │             │            │
     │                │<───────────────────                │                  │                  │             │            │
     │ 17. HTTP 响应   │                │                │                  │                  │             │            │
     │<───────────────│                │                │                  │                  │             │            │
     │                │                   │                │                  │                  │             │            │
```

---

*文档持续更新中...*
