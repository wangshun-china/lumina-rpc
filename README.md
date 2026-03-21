# Lumina-RPC

<div align="center">

**面向可观测性的轻量 RPC 框架**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Netty](https://img.shields.io/badge/Netty-4.1-blue.svg)](https://netty.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[在线演示](http://42.193.105.133:3000) · [架构设计](#-架构设计) · [CI/CD 流程](#-cicd-流程)

</div>

---

## 项目简介

**Lumina-RPC** 是一款包含「核心通信 SDK」、「云端控制面」与「可视化监控面板」的轻量 RPC 框架。

### 核心能力

- **高性能通信层**：基于 Netty 的 NIO 通信，支持自定义协议编解码，解决 TCP 粘包/半包问题
- **服务注册发现**：自研控制面注册中心，支持心跳检测与故障剔除，AP 架构保证高可用
- **动态代理**：ByteBuddy 生成代理类，透明化远程调用，性能优于 JDK 动态代理
- **负载均衡**：SPI 机制支持 5 种负载策略（RoundRobin、Random、WeightedRoundRobin、LeastActive、ConsistentHash）
- **服务预热**：支持预热权重，新实例逐步承接流量，避免冷启动问题
- **集群容错**：4 种容错策略（Failover、Failfast、Failsafe、Forking），满足不同业务场景
- **Mock 引擎**：动态降级能力，支持短路模式与数据篡改模式
- **熔断限流**：滑动窗口熔断器、令牌桶限流器，支持动态配置
- **链路追踪**：分布式调用链追踪，Span 收集与瀑布图可视化
- **实时监控**：SSE 推送 Mock 规则变更，毫秒级生效
- **可视化面板**：Vue 3 + Vue Flow 服务拓扑图，实时监控

---

## 架构设计

```
+-------------------------------------------------------------------------+
|                           前端监控层                                      |
|  +---------------------------------------------------------------------+|
|  |  Vue 3 Dashboard (lumina-dashboard)                                 ||
|  |  +-- 服务拓扑图 (Vue Flow)     +-- Mock 规则配置                      ||
|  |  +-- 链路追踪 (瀑布图)         +-- 熔断限流配置                       ||
|  |  +-- 消费者操作台             +-- 实时遥测数据                        ||
|  +---------------------------------------------------------------------+|
+-------------------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------------------+
|                           控制面层                                       |
|  +---------------------------------------------------------------------+|
|  |  Control Plane (lumina-control-plane)                               ||
|  |  +-- 服务注册中心 (HTTP API)   +-- Mock 规则管理                      ||
|  |  +-- 链路追踪数据存储          +-- 保护配置管理                        ||
|  |  +-- 心跳检测 & 健康管理       +-- SSE 实时推送                        ||
|  |  +-- MySQL 持久化                                                    ||
|  +---------------------------------------------------------------------+|
+-------------------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------------------+
|                        数据面层 (RPC 核心)                                |
|  +-----------------------+  +-----------------------+                    |
|  |  lumina-rpc-core      |  | lumina-rpc-protocol   |                    |
|  |  +-- 动态代理          |  | +-- 协议编解码         |                    |
|  |  +-- 服务发现          |  | +-- 消息序列化         |                    |
|  |  +-- 负载均衡(5种)     |  | +-- 心跳机制           |                    |
|  |  +-- 集群容错(4种)     |  | +-- 连接池管理         |                    |
|  |  +-- Mock 引擎         |  | +-- 待处理请求管理     |                    |
|  |  +-- 熔断限流          |  |                       |                    |
|  |  +-- 链路追踪          |  |                       |                    |
|  |  +-- SPI 扩展机制      |  |                       |                    |
|  +-----------------------+  +-----------------------+                    |
+-------------------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------------------+
|                           业务服务层                                     |
|  +---------------+  +---------------+  +---------------+                |
|  | Engine Service|  | Radar Service |  |Command Service|                |
|  |   (Provider)  |  |   (Provider)  |  |  (Consumer)   |                |
|  |    曲率引擎    |  |    深空雷达    |  |   舰队指挥     |                |
|  +---------------+  +---------------+  +---------------+                |
+-------------------------------------------------------------------------+
```

### 架构亮点：控制面/数据面分离

- **数据面**：RPC 核心通信层，负责服务调用、负载均衡、Mock 拦截、熔断限流
- **控制面**：服务注册中心，管理服务元数据、Mock 规则、保护配置、链路数据
- **优势**：符合云原生设计理念，控制逻辑与数据转发解耦，便于独立扩展和运维

### 完整 RPC 调用链路

```
Consumer 端调用链:
1. 业务代码调用代理方法 -> RpcClientHandler.invoke()
2. Mock 规则匹配（短路/篡改模式）
3. 构建 RpcRequest，生成 Trace ID（雪花算法）
4. 服务发现（从本地缓存获取实例列表）
5. 集群容错策略调用（Failover/Failfast/Failsafe/Forking）
6. 限流检查（令牌桶算法）
7. 熔断器状态检查（滑动窗口统计）
8. 负载均衡选择目标地址
9. 从连接池获取 Channel
10. 构建 RpcMessage，序列化请求体
11. Netty 发送请求
12. 注册 CompletableFuture 等待响应

Provider 端处理链:
1. Netty 接收字节流，RpcDecoder 解码
2. 反序列化请求体
3. 优雅停机检查
4. 从本地注册表获取服务实现
5. 反射调用目标方法
6. 构建响应，序列化返回

Consumer 端收响应:
1. Netty 接收响应，NettyClientHandler 处理
2. 完成 CompletableFuture
3. 熔断器记录成功/失败
4. 统计上报（请求数、延迟）
5. 链路追踪 Span 结束并上报
6. 归还 Channel 到连接池
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行时环境（支持虚拟线程预览） |
| Spring Boot | 3.3.0 | 应用框架 |
| Netty | 4.1.100 | 高性能网络通信 |
| ByteBuddy | 1.14.9 | 运行时动态代理生成 |
| Jackson | 2.15.2 | JSON 序列化 |
| Spring Data JPA | 3.3.0 | 数据持久化 |
| MySQL | 8.0 | 数据库 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | ^3.4.0 | 前端框架 |
| Vue Flow | ^1.33.0 | 服务拓扑图可视化 |
| ECharts | ^5.5.0 | 链路追踪瀑布图、趋势图表 |
| Axios | ^1.6.0 | HTTP 客户端 |
| Element Plus | ^2.5.0 | UI 组件库 |

### 运维

| 技术 | 用途 |
|------|------|
| Docker | 容器化部署 |
| Docker Compose | 多容器编排 |
| GitHub Actions | CI/CD 自动化 |
| 阿里云 ACR | 镜像仓库 |

---

## 核心特性

### 1. 自定义 RPC 协议

#### 协议头设计（17 字节）

```
+--------+---------+---------------+-------------+-----------+-------------+
| Magic  | Version | Serializer    | MessageType | RequestId | Data Length |
| 2 bytes| 1 byte  | 1 byte        | 1 byte      | 8 bytes   | 4 bytes     |
+--------+---------+---------------+-------------+-----------+-------------+
|                           Header (17 bytes)              |     Body     |
+----------------------------------------------------------+-------------+
```

#### 字段说明

| 字段 | 长度 | 说明 |
|------|------|------|
| Magic Number | 2 bytes | `0x4C55` ("LU")，用于快速识别协议 |
| Version | 1 byte | 协议版本号，当前为 1 |
| Serializer Type | 1 byte | 序列化类型：JSON=0, KRYO=1 |
| Message Type | 1 byte | 消息类型：REQUEST=0, RESPONSE=1, HEARTBEAT=2 |
| Request ID | 8 bytes | 雪花算法生成的唯一 ID，用于请求响应匹配 |
| Data Length | 4 bytes | Body 长度，用于解决 TCP 粘包问题 |

#### TCP 粘包/半包解决方案

使用 Netty 的 `LengthFieldBasedFrameDecoder` 自动处理：

```java
public class RpcDecoder extends LengthFieldBasedFrameDecoder {
    public RpcDecoder() {
        // maxFrameLength: 65536 - 最大帧长度 64KB
        // lengthFieldOffset: 13 - Data Length 字段从第 13 字节开始
        // lengthFieldLength: 4 - Data Length 字段占 4 字节
        // lengthAdjustment: 0 - 长度字段值就是 Body 长度
        // initialBytesToStrip: 0 - 不跳过任何字节
        super(65536, 13, 4, 0, 0);
    }
}
```

**工作原理**：
1. 协议头前 13 字节是固定头信息，第 13-16 字节是 `Data Length`
2. `LengthFieldBasedFrameDecoder` 读取 `Data Length`，知道 Body 有多长
3. 自动截取完整的一帧消息，解决粘包（多条消息粘在一起）和半包（消息不完整）

---

### 2. 多策略负载均衡

支持 5 种负载均衡策略，通过 SPI 机制可扩展：

| 策略 | 说明 | 适用场景 | 实现类 |
|------|------|----------|--------|
| RoundRobin | 轮询选择 | 流量均匀分布 | `RoundRobinLoadBalancer` |
| Random | 加权随机 | 服务器性能差异 | `RandomLoadBalancer` |
| WeightedRoundRobin | 加权轮询 | 按权重分配流量 | `WeightedRoundRobinLoadBalancer` |
| LeastActive | 最少活跃调用 | 长连接场景 | `LeastActiveLoadBalancer` |
| ConsistentHash | 一致性哈希 | 需要会话保持 | `ConsistentHashLoadBalancer` |

#### LeastActive 实现（最少活跃调用）

```java
public InetSocketAddress selectInstance(List<ServiceInstance> instances, String serviceName) {
    ServiceInstance selected = null;
    int minActive = Integer.MAX_VALUE;
    int maxWeight = -1;

    for (ServiceInstance instance : instances) {
        String addressKey = instance.getAddress();
        int active = activeCounter.getActiveCount(addressKey);
        int effectiveWeight = instance.getEffectiveWeight();

        // 优先选择活跃数最少的
        // 如果活跃数相同，选择权重更高的
        if (active < minActive || (active == minActive && effectiveWeight > maxWeight)) {
            minActive = active;
            maxWeight = effectiveWeight;
            selected = instance;
        }
    }

    return new InetSocketAddress(selected.getHost(), selected.getPort());
}
```

#### 服务预热权重支持

```java
// 服务预热权重支持
ServiceInstance instance = new ServiceInstance();
instance.setWarmupWeight(50);  // 预热期间权重
instance.setWeight(100);       // 正常权重
instance.setWarmupPeriod(60000);  // 预热周期 60 秒

// 获取有效权重（预热期间逐步增加）
int effectiveWeight = instance.getEffectiveWeight();
```

---

### 3. 集群容错策略

支持 4 种集群容错策略：

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| Failover | 失败自动重试其他服务器 | 读操作，幂等写操作 |
| Failfast | 快速失败，立即报错 | 非幂等写操作 |
| Failsafe | 失败安全，忽略异常 | 日志记录等非关键操作 |
| Forking | 并行调用多个服务器，任一成功即返回 | 实时性要求高的场景 |

#### Failover 实现（失败自动重试）

```java
private void doInvokeWithRetry(ClusterInvocation invocation, boolean async,
                                int attempt, int maxAttempts,
                                List<InetSocketAddress> failedAddresses,
                                CircuitBreaker circuitBreaker, ...) {
    // 选择一个未失败的地址
    InetSocketAddress address = invocation.selectAddress(failedAddresses);

    // 记录活跃调用（用于 LeastActive 负载均衡）
    ActiveCounter.getInstance().increment(addressKey);

    // 异步调用
    CompletableFuture<RpcResponse> responseFuture = RpcInvoker.invokeAsync(...);

    responseFuture.whenComplete((response, ex) -> {
        // 减少活跃调用计数
        ActiveCounter.getInstance().decrement(addressKey);

        if (ex != null && attempt < maxAttempts) {
            // 失败后重试其他服务器
            failedAddresses.add(address);
            doInvokeWithRetry(invocation, async, attempt + 1, maxAttempts,
                    failedAddresses, circuitBreaker, ...);
        }
    });
}
```

---

### 4. Mock 引擎

#### 两种 Mock 模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| SHORT_CIRCUIT | 短路模式：直接返回 Mock 数据，不发起网络请求 | 服务不可用时的快速降级 |
| TAMPER | 篡改模式：先发起真实请求，再合并 Mock 数据 | 数据脱敏、字段篡改 |

#### 条件匹配能力

- **多参数组合匹配**：支持多参数 AND 关系组合
- **操作符支持**：`equals`, `contains`, `regex`, `gt`, `lt`, `gte`, `lte`
- **占位符篡改**：`{{base}}` 保留原始值，支持数学运算 `{{base}}+100`

```java
// Mock 规则示例
MockRule rule = new MockRule();
rule.setServiceName("EngineService");
rule.setMethodName("calculate");
rule.setMode("TAMPER");  // 篡改模式

// 条件匹配
MockCondition condition = new MockCondition();
condition.setParameterName("engineType");
condition.setOperator("equals");
condition.setValue("warp");
rule.setConditions(List.of(condition));

// 响应篡改
rule.setResponseBody("{\"power\": {{base}} * 0.8}");  // 功率打 8 折
```

#### SSE 实时推送

Mock 规则变更通过 Server-Sent Events 实时推送到所有消费者：

```java
// 控制面推送
@GetMapping("/api/v1/sse/mock-rules/subscribe")
public SseEmitter subscribeMockRules() {
    SseEmitter emitter = new SseEmitter(0L);  // 无超时
    emitters.add(emitter);
    return emitter;
}

// 规则变更时广播
public void broadcastRuleChange(String serviceName, Long ruleId, String action) {
    for (SseEmitter emitter : emitters) {
        emitter.send(SseEmitter.event()
                .name("rule-change")
                .data(objectMapper.writeValueAsString(event)));
    }
}
```

---

### 5. 熔断限流保护

#### 滑动窗口熔断器

```java
// 熔断器状态机
public enum State {
    CLOSED,    // 关闭状态：正常请求
    OPEN,      // 打开状态：拒绝所有请求
    HALF_OPEN  // 半开状态：放行部分请求探测
}

// 滑动窗口统计
private final AtomicInteger[] successWindow;  // 成功计数窗口
private final AtomicInteger[] failureWindow;  // 失败计数窗口

// 检查阈值
private void checkThreshold() {
    int requests = totalRequests.get();
    int errors = totalErrors.get();
    double errorRate = (double) errors / requests * 100;

    if (errorRate >= errorThreshold) {
        state.compareAndSet(State.CLOSED, State.OPEN);
    }
}
```

#### 令牌桶限流器

```java
// 令牌桶算法
public class TokenBucket {
    private final long capacity;        // 桶容量
    private final long refillRate;      // 每秒补充令牌数
    private final AtomicLong tokens;    // 当前令牌数
    private final AtomicLong lastRefillTime;

    public boolean tryAcquire() {
        refill();  // 补充令牌
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime.get();
        if (elapsed > 0) {
            long newTokens = elapsed * refillRate / 1000;
            tokens.set(Math.min(capacity, tokens.get() + newTokens));
            lastRefillTime.set(now);
        }
    }
}
```

#### 动态配置

```java
// 动态熔断器配置
ProtectionConfig config = new ProtectionConfig();
config.setCircuitBreakerEnabled(true);
config.setFailureRateThreshold(50);         // 失败率阈值 50%
config.setSlowCallDurationThreshold(2000);  // 慢调用阈值 2s
config.setSlowCallRateThreshold(30);        // 慢调用率阈值 30%
config.setWaitDurationInOpenState(10000);   // 熔断等待时间 10s

// 限流配置
config.setRateLimiterEnabled(true);
config.setRateLimitPermits(100);  // 每秒 100 次
```

---

### 6. 分布式链路追踪

#### Span 数据结构

```java
public class Span {
    private String traceId;       // 链路 ID（全局唯一）
    private String spanId;        // 当前 Span ID
    private String parentSpanId;  // 父 Span ID
    private String serviceName;   // 服务名
    private String methodName;    // 方法名
    private String kind;          // CLIENT/SERVER
    private long startTime;       // 开始时间
    private long duration;        // 耗时（毫秒）
    private boolean success;      // 是否成功
    private String errorMessage;  // 错误信息
    private Map<String, String> tags;  // 标签
}
```

#### Trace ID 传递

```java
// Consumer 端生成/传递 Trace ID
private RpcRequest buildRpcRequest(Method method, Object[] args) {
    String traceId = TraceContext.getTraceId();  // 从 ThreadLocal 获取
    if (traceId == null) {
        traceId = TraceContext.generateTraceId();  // 雪花算法生成
        TraceContext.setTraceId(traceId);
    }
    request.setTraceId(traceId);

    // 设置 MDC 以便日志自动包含 Trace ID
    MDC.put("traceId", traceId);
}

// Provider 端接收 Trace ID
private RpcResponse invokeService(RpcRequest request) {
    String traceId = request.getTraceId();
    if (traceId != null) {
        TraceContext.setTraceId(traceId);  // 设置到当前线程
        MDC.put("traceId", traceId);
    }
}
```

#### Span 收集与上报

```java
// Span 收集器
public class SpanCollector {
    public Span startClientSpan(String serviceName, String methodName, String parentSpanId) {
        Span span = new Span();
        span.setTraceId(TraceContext.getTraceId());
        span.setSpanId(generateSpanId());
        span.setParentSpanId(parentSpanId);
        span.setKind("CLIENT");
        span.setStartTime(System.currentTimeMillis());
        return span;
    }

    public void endSpan(Span span) {
        span.setDuration(System.currentTimeMillis() - span.getStartTime());
        span.setSuccess(true);
        reportSpan(span);  // 上报到控制面
    }
}
```

---

### 7. 连接池管理

#### Channel 池设计

```java
public class ChannelPoolManager {
    // 地址 -> ChannelPool 映射
    private final ConcurrentHashMap<String, ChannelPool> poolMap;

    // 全局最大连接数
    private final int globalMaxChannels = 100;

    // 获取 Channel（阻塞直到获取成功或超时）
    public Channel acquire(InetSocketAddress address, long timeoutMs) {
        ChannelPool pool = getOrCreatePool(address);

        while (true) {
            // 1. 尝试从池中借用
            Channel channel = pool.borrowChannel();
            if (channel != null && channel.isActive()) {
                return channel;
            }

            // 2. 如果可以创建新连接
            if (pool.canCreate() && globalChannelCount.get() < globalMaxChannels) {
                Channel newChannel = channelFactory.createChannel(address);
                pool.addChannel(newChannel);
                globalChannelCount.incrementAndGet();
                return newChannel;
            }

            // 3. 检查超时
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw new RuntimeException("Timeout acquiring channel");
            }

            // 4. 等待重试
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }
}
```

#### 连接池配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| minChannels | 2 | 每个地址最小连接数 |
| maxChannels | 10 | 每个地址最大连接数 |
| globalMaxChannels | 100 | 全局最大连接数 |
| borrowTimeout | 3000ms | 借用连接超时时间 |

---

### 8. SPI 扩展机制

#### 负载均衡器扩展点

```java
// 接口定义
public interface LoadBalancer {
    InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName);
    InetSocketAddress selectInstance(List<ServiceInstance> instances, String serviceName);
    String getName();
}

// SPI 加载器
static {
    // 1. 通过 Java SPI 加载外部实现
    ServiceLoader<LoadBalancer> loader = ServiceLoader.load(LoadBalancer.class);
    for (LoadBalancer loadBalancer : loader) {
        registerLoadBalancer(loadBalancer);
    }

    // 2. 注册内置实现
    registerLoadBalancer(new RoundRobinLoadBalancer());
    registerLoadBalancer(new RandomLoadBalancer());
    // ...
}

// 自定义负载均衡器
public class MyLoadBalancer implements LoadBalancer {
    @Override
    public String getName() {
        return "my-lb";
    }

    // ... 实现接口方法
}

// 注册到 META-INF/services/com.lumina.rpc.core.spi.LoadBalancer
```

#### 序列化器扩展点

```java
public interface Serializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] bytes, Class<T> clazz);
    byte getType();
    String getName();
}

// 内置实现：JsonSerializer, KryoSerializer
// 可通过 META-INF/services/com.lumina.rpc.protocol.spi.Serializer 扩展
```

---

### 9. 异步转同步机制

使用 `CompletableFuture` 实现异步转同步：

```java
// RpcInvoker - 发送请求
public static RpcResponse invoke(...) {
    // 1. 注册待处理请求
    CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
    pendingManager.addPendingRequest(request.getRequestId(), responseFuture);

    // 2. 发送请求（异步）
    channel.writeAndFlush(message);

    // 3. 阻塞等待响应
    return responseFuture.get(timeout, TimeUnit.MILLISECONDS);
}

// NettyClientHandler - 响应回调
public void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
    RpcResponse response = (RpcResponse) msg.getBody();
    pendingManager.completePendingRequest(response);  // 完成 Future
}

// PendingRequestManager - 管理待处理请求
private final ConcurrentHashMap<String, CompletableFuture<RpcResponse>> pendingRequests;

public void addPendingRequest(String requestId, CompletableFuture<RpcResponse> future) {
    pendingRequests.put(requestId, future);
}

public void completePendingRequest(RpcResponse response) {
    CompletableFuture<RpcResponse> future = pendingRequests.remove(response.getRequestId());
    if (future != null) {
        future.complete(response);
    }
}
```

---

### 10. 服务注册与发现

#### Provider 注册流程

```java
private void registerToControlPlane() {
    // 1. 提取服务元数据（接口、方法、参数类型）
    String metadataJson = ServiceMetadataExtractor.extractMetadataBatch(publishedInterfaces);

    // 2. 向控制面发送 HTTP 注册请求
    ServiceRegistryClient.init(primaryServiceName, registryHost, port, "", metadataJson);

    // 3. 启动心跳线程（30 秒一次）
    scheduler.scheduleAtFixedRate(() -> heartbeat(), 30, 30, TimeUnit.SECONDS);
}
```

#### Consumer 发现流程（AP 架构高可用）

```java
public static void refreshAllServices() {
    try {
        List<ServiceInstance> instances = fetchAllInstances();
        if (instances == null || instances.isEmpty()) {
            // 控制面不可用时，保留上次缓存（AP 架构）
            logger.warn("Keeping {} cached instances", lastSuccessfulInstances.size());
            return;  // 不清空缓存！
        }
        ServiceDiscovery.updateServiceInstances(serviceName, instances);
    } catch (Exception e) {
        // 任何异常都不清空缓存
        logger.error("Keeping cached instances...");
    }
}
```

#### 心跳检测机制

- **Provider 心跳上报**：间隔 30 秒
- **控制面超时判定**：90 秒未收到心跳 → 标记为 DOWN
- **定时清理**：每 60 秒执行，删除 DOWN 超过 1 小时的僵尸实例

---

## CI/CD 流程

> **完整的自动化部署流水线，push 即部署**

### 流程图

```
+-------------------------------------------------------------------------+
|                    GitHub Actions Pipeline                               |
+-------------------------------------------------------------------------+
|                                                                         |
|  +----------+    +----------+    +----------+    +----------+           |
|  | Checkout | -> | Build    | -> | Docker   | -> | Deploy   |           |
|  |   Code   |    | (Maven)  |    |  Build   |    |   SSH    |           |
|  +----------+    +----------+    +----------+    +----------+           |
|                                                                         |
|  触发条件：push to master                                               |
|  执行时间：约 5-8 分钟                                                  |
|  部署目标：阿里云 ECS (42.193.105.133)                                  |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 详细步骤

```yaml
# .github/workflows/deploy.yml

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      # 1. 检出代码
      - name: Checkout code
        uses: actions/checkout@v4

      # 2. 配置 JDK 21
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      # 3. 构建后端 (Maven)
      - name: Build backend with Maven
        run: mvn clean package -DskipTests

      # 4. 构建前端 (Vue 3 + pnpm)
      - name: Build frontend
        run: |
          cd lumina-dashboard
          npm install -g pnpm
          pnpm install
          pnpm build

      # 5. 登录阿里云镜像仓库
      - name: Login to Aliyun ACR
        uses: docker/login-action@v3

      # 6. 构建 & 推送 5 个 Docker 镜像
      - name: Build and push Docker images
        run: |
          docker build -f Dockerfile.control-plane -t registry:control-plane .
          docker build -f Dockerfile.sample-engine -t registry:engine .
          docker build -f Dockerfile.sample-radar -t registry:radar .
          docker build -f Dockerfile.sample-command -t registry:command .
          docker build -f Dockerfile.front -t registry:dashboard .
          docker push ...

      # 7. SSH 远程部署
      - name: Deploy with SSH
        uses: appleboy/ssh-action@master
        with:
          script: |
            docker-compose down
            docker-compose pull
            docker-compose up -d
```

### 部署架构

```
+-------------------------------------------------------------------------+
|                     阿里云 ECS 服务器                                    |
|                                                                         |
|  +-------------------------------------------------------------------+  |
|  |                    Docker Compose                                  |  |
|  |                                                                   |  |
|  |  +---------+  +---------+  +---------+  +---------+               |  |
|  |  | MySQL   |  |Control  |  | Engine  |  | Radar   |               |  |
|  |  |  :3306  |  | Plane   |  | :8081   |  | :8082   |               |  |
|  |  |         |  | :8080   |  |         |  |         |               |  |
|  |  +---------+  +---------+  +---------+  +---------+               |  |
|  |                                                                   |  |
|  |  +---------+  +---------+                                        |  |
|  |  | Command |  |Dashboard|                                        |  |
|  |  | :8083   |  | :3000   |  <- Nginx 反向代理                      |  |
|  |  |         |  |         |                                        |  |
|  |  +---------+  +---------+                                        |  |
|  |                                                                   |  |
|  +-------------------------------------------------------------------+  |
|                              lumina-net (Docker Bridge Network)        |
+-------------------------------------------------------------------------+
```

### 部署特点

- **零停机部署**：容器平滑切换，服务不中断
- **滚动更新**：逐个服务替换，确保可用性
- **健康检查**：等待 MySQL 就绪后再启动应用服务
- **资源限制**：每个容器配置 CPU/内存限制，防止资源争抢
- **日志管理**：限制日志文件大小，防止磁盘占满
- **环境变量解耦**：本地开发与 Docker 部署通过环境变量自动切换

---

## 项目结构

```
lumina-rpc/
+-- lumina-rpc-protocol/        # 协议层：编解码、消息定义
|   +-- src/main/java/
|       +-- codec/              # RpcEncoder, RpcDecoder (解决粘包)
|       +-- transport/          # NettyClient, NettyClientHandler
|       +-- pool/               # 连接池管理 (ChannelPoolManager)
|       +-- common/             # PendingRequestManager (异步转同步)
|       +-- spi/                # 序列化器 SPI
|
+-- lumina-rpc-core/            # 核心层：动态代理、服务发现
|   +-- src/main/java/
|       +-- annotation/         # @LuminaService, @LuminaReference
|       +-- proxy/              # ByteBuddy 动态代理 (RpcClientHandler)
|       +-- discovery/          # 服务发现客户端 (AP 架构高可用)
|       +-- cluster/            # 集群容错 (Failover/Failfast/Failsafe/Forking)
|       +-- spi/                # 负载均衡器 SPI (5种策略)
|       +-- protection/         # 熔断器、限流器
|       +-- trace/              # 链路追踪 (Span 收集、上报)
|       +-- mock/               # Mock 规则引擎 (短路/篡改模式)
|       +-- stats/              # 请求统计上报
|       +-- spring/             # Spring 自动配置
|
+-- lumina-control-plane/       # 控制面：注册中心
|   +-- src/main/java/
|       +-- controller/         # REST API
|       +-- service/            # 业务逻辑
|       +-- entity/             # JPA 实体 (服务、Mock规则、Span、请求统计)
|       +-- sse/                # SSE 实时推送
|       +-- repository/         # 数据访问
|
+-- lumina-dashboard/           # 前端监控面板
|   +-- src/
|       +-- views/              # 页面组件 (拓扑图、链路追踪、Mock配置等)
|       +-- components/         # 通用组件
|       +-- api/                # API 模块化封装
|       +-- types/              # TypeScript 类型定义
|
+-- lumina-sample-engine/       # 示例服务：曲率引擎 (Provider)
+-- lumina-sample-radar/        # 示例服务：深空雷达 (Provider)
+-- lumina-sample-command/      # 示例服务：舰队指挥 (Consumer)
|
+-- docker-compose.yml          # 容器编排
+-- Dockerfile.*                # 各服务 Dockerfile
+-- .github/workflows/          # CI/CD 配置
```

---

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.8+
- Docker & Docker Compose

### 本地运行

```bash
# 1. 克隆项目
git clone https://github.com/xixi-box/lumina-rpc.git
cd lumina-rpc

# 2. 构建项目
mvn clean package -DskipTests

# 3. 启动 MySQL
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=lumina123 \
  -e MYSQL_DATABASE=lumina \
  -e MYSQL_USER=lumina \
  -e MYSQL_PASSWORD=lumina123 \
  -p 3306:3306 \
  mysql:8.0

# 4. 启动控制面
java -jar lumina-control-plane/target/lumina-control-plane-exec.jar

# 5. 启动服务提供者 (Engine & Radar)
java -jar lumina-sample-engine/target/lumina-sample-engine-exec.jar
java -jar lumina-sample-radar/target/lumina-sample-radar-exec.jar

# 6. 启动服务消费者 (Command)
java -jar lumina-sample-command/target/lumina-sample-command-exec.jar

# 7. 启动前端
cd lumina-dashboard
pnpm install && pnpm dev
```

### Docker 部署

```bash
# 一键启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 控制面 API | http://localhost:8080 |
| Engine 服务 | http://localhost:8081 |
| Radar 服务 | http://localhost:8082 |
| Command 服务 | http://localhost:8083 |
| 前端面板 | http://localhost:3000 |

---

## 功能截图

### 服务拓扑图

实时展示服务注册状态、调用关系、健康状态。

### 链路追踪

瀑布图展示分布式调用链，Span 耗时、状态一目了然。

### Mock 规则配置

可视化配置动态降级规则，支持条件匹配、响应篡改。

### 熔断限流配置

动态调整熔断器阈值、限流参数，实时生效。

### 消费者操作台

直接调用远程服务，实时查看响应结果和耗时。

---

## 性能压测

### 测试环境

| 项目 | 配置 |
|------|------|
| 操作系统 | Windows 11 Pro |
| CPU | AMD Ryzen 9 7845HX with Radeon Graphics |
| 内存 | 镁光32 GB DDR5 5200MHz(16GB+16GB) |
| JDK | 21 |
| 测试工具 | Apache JMeter 5.6.3 |
| 被测服务 | Engine (Provider) + Radar (Provider) + Command (Consumer) |
| 网络环境 | 本地 localhost |

### 测试接口

```
POST http://127.0.0.1:8083/api/command/proxy-invoke
Content-Type: application/json

{"serviceName":"engine-service","methodName":"getWarpStatus","args":["SHIP-xxx"]}
{"serviceName":"radar-service","methodName":"scanEnemies","args":["SECTOR-xxx"]}
```

> **说明**：测试调用的是真实 RPC 方法：`EngineService.getWarpStatus()` 和 `RadarService.scanEnemies()`，这两个方法无延迟，立即返回模拟数据。

### 极限压测数据

| 线程数 | QPS | 平均响应 | P90 | P95 | P99 | 错误率 | 状态 |
|--------|-----|----------|-----|-----|-----|--------|------|
| 500 | **6863** | 70ms | 55ms | 66ms | 671ms | 0% | 🟢 最佳性能 |
| 1000 | **6753** | 71ms | 83ms | 169ms | 1007ms | 0% | 🟢 稳定 |
| 2000 | **6811** | 70ms | 65ms | 75ms | 857ms | 0% | 🟢 表现优异 |
| 3000 | **6793** | 70ms | 70ms | 91ms | 836ms | 0% | 🟢 高压稳定 |
| 5000 | **7185** | 66ms | 66ms | 84ms | 768ms | 0% | 🟢 极限压测 |

> **说明**：测试过程中观察到 `Active: 500`，表示 JMeter 实际活跃线程数为 500 左右。这是由于 HTTP 请求响应较快（~70ms），线程完成请求后立即进入下一轮，导致并发线程数不会持续达到配置的最大值。所以虽然QPS未达到拐点，可是再增加线程的意义已经不大，因此上述测试更准确地反映了 **"每秒发起的请求数"** 而非真实的并发连接数。

### 性能趋势分析

```
QPS                                      P99 响应时间
│                                           │
│           ┌─────┐ 7185                    │  ┌────┐ 1007ms
│           │     │                         │  │    │
│  ┌────┐   │     │                         │  │    ├────┐ 857ms
│  │    │   │     │                         │  │    │    ├────┐ 836ms
│  │    ├───┤     ├────┐ 6863               │  │    │    │    ├────┐ 768ms
│  │    │   │     │    │                    │  │    │    │    │    ├────┐ 671ms
│  │    │   │     │    │                    │  └────┴────┴────┴────┴────┘
│  │    │   │     │    │                    │
└──┴────┴───┴─────┴────┴──────→ 线程数      └───────────────────────────→ 线程数
   500  1000 2000  3000  5000               500  1000 2000 3000  5000
```

**关键发现：**
- **峰值 QPS**: ~7200（5000线程配置下）
- **稳定 QPS**: ~6700-6800，各线程数下表现一致
- **P99 响应**: 稳定在 700-1000ms 区间
- **零错误率**: 全程 0%，框架稳定性极佳
- **无性能拐点**: 500-5000线程配置下 QPS 基本持平，框架无明显瓶颈

### 压测结论

| 指标 | 数值 | 说明 |
|------|------|------|
| **峰值 QPS** | ~7200 | 单机最佳性能 |
| **稳定 QPS** | ~6700-6800 | 各线程数下表现一致 |
| **平均响应** | ~70ms | 毫秒级延迟 |
| **P99 响应** | ~700-1000ms | 99%请求在1秒内完成 |
| **极限承受** | 5000 线程配置 | 不崩溃、零错误 |

**总结**：Lumina-RPC 框架在单机环境下可稳定支撑 **6500-7000 QPS**，平均响应 **~70ms**，P99 响应 **< 1秒**，全程 **0% 错误率**。框架在高并发下无明显性能拐点，表现稳定。如需更高 QPS，建议水平扩展 Provider 实例。

### 基础性能指标

| 指标 | 数值 |
|------|------|
| 单次 RPC 调用耗时 | < 10ms (本地网络) |
| 心跳间隔 | 30s |
| 服务过期时间 | 90s |
| 连接超时 | 5s |
| 请求超时 | 5s (可配置) |
| 连接池大小 | min=2, max=10 (per address) |
| 全局最大连接数 | 100 |

---

## 优化建议

### 性能优化

| 问题 | 位置 | 优化建议 |
|------|------|----------|
| EventLoop 被业务阻塞 | `DefaultRpcRequestHandler` | 提交到独立线程池或虚拟线程 |
| 滑动窗口基于请求数 | `CircuitBreaker` | 改为时间维度滑动窗口 |
| 连接池等待自旋 | `ChannelPoolManager` | 使用 Condition 或 Semaphore |
| JSON 序列化性能 | `JsonSerializer` | Kryo/FST 替换，性能提升 5-10x |

### 可靠性优化

| 问题 | 优化建议 |
|------|----------|
| 心跳风暴风险 | 心跳增加随机抖动 |
| 服务发现单点 | 多控制面实例 + 客户端负载均衡 |
| Span 上报失败丢弃 | 增加本地磁盘缓冲队列 |
| 连接断开无自动重连 | 增加 Netty 重连 Handler |

---

## 未来规划

- [x] 多策略负载均衡 (已完成 5 种)
- [x] 链路追踪与可视化
- [x] 熔断限流保护
- [x] 集群容错策略 (Failover/Failfast/Failsafe/Forking)
- [ ] 支持 gRPC 协议
- [ ] 集成 OpenTelemetry 可观测性
- [ ] 支持 Java 21 虚拟线程
- [ ] 接入 Nacos/Apollo 配置中心
- [ ] 支持 K8s 部署

---

## 作者

**Wang Shun**

- GitHub: [@xixi-box](https://github.com/xixi-box)

---

## License

MIT License

---

<div align="center">

**如果这个项目对你有帮助，请给一个 Star！**

</div>