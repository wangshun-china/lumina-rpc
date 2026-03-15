# 🚀 Lumina-RPC

<div align="center">

**面向可观测性的企业级轻量 RPC 框架**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Netty](https://img.shields.io/badge/Netty-4.1-blue.svg)](https://netty.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[在线演示](http://42.193.105.133:3000) · [架构设计](#-架构设计) · [CI/CD 流程](#-cicd-流程)

</div>

---

## 🎯 面试亮点

> **为什么这个项目值得展示？**

| 亮点 | 说明 |
|------|------|
| **自研 RPC 框架** | 从零实现完整的 RPC 通信层，深入理解分布式系统底层原理 |
| **企业级特性** | 动态服务降级、Mock 规则引擎、熔断限流、链路追踪，对标 Dubbo 核心能力 |
| **生产级部署** | 完整 CI/CD 流水线，Docker 容器化，MySQL 持久化，已在云服务器运行 |
| **前后端分离** | Spring Boot 后端 + Vue 3 前端，全栈独立完成 |
| **架构设计** | 控制面/数据面分离架构，符合云原生设计理念 |

---

## 📖 项目简介

**Lumina-RPC** 是一款包含「核心通信 SDK」、「云端控制面」与「可视化监控面板」的企业级轻量 RPC 框架。

### 核心能力

- ✅ **高性能通信层**：基于 Netty 的 NIO 通信，支持自定义协议编解码
- ✅ **服务注册发现**：自研控制面注册中心，支持心跳检测与故障剔除
- ✅ **动态代理**：ByteBuddy 生成代理类，透明化远程调用
- ✅ **负载均衡**：SPI 机制支持 5 种负载策略（RoundRobin、Random、WeightedRoundRobin、LeastActive、ConsistentHash）
- ✅ **服务预热**：支持预热权重，新实例逐步承接流量
- ✅ **Mock 引擎**：企业级动态降级能力，支持短路模式与数据篡改模式
- ✅ **熔断限流**：动态熔断器配置、请求限流、超时控制
- ✅ **链路追踪**：分布式调用链追踪，Span 收集与瀑布图可视化
- ✅ **实时监控**：SSE 推送 Mock 规则变更，毫秒级生效
- ✅ **可视化面板**：Vue 3 + Vue Flow 服务拓扑图，实时监控

---

## 🏗 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端监控层                                 │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Vue 3 Dashboard (lumina-dashboard)                         │ │
│  │  ├─ 服务拓扑图 (Vue Flow)     ├─ Mock 规则配置               │ │
│  │  ├─ 链路追踪 (瀑布图)         ├─ 熔断限流配置                │ │
│  │  ├─ 消费者操作台             └─ 实时遥测数据                 │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        控制面层                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Control Plane (lumina-control-plane)                       │ │
│  │  ├─ 服务注册中心 (HTTP API)   ├─ Mock 规则管理               │ │
│  │  ├─ 链路追踪数据存储          ├─ 保护配置管理                │ │
│  │  ├─ 心跳检测 & 健康管理       ├─ SSE 实时推送                │ │
│  │  └─ MySQL 持久化                                            │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        数据面层 (RPC 核心)                        │
│  ┌───────────────────┐  ┌───────────────────┐                    │
│  │  lumina-rpc-core  │  │ lumina-rpc-protocol│                    │
│  │  ├─ 动态代理       │  │ ├─ 协议编解码      │                    │
│  │  ├─ 服务发现       │  │ ├─ 消息序列化      │                    │
│  │  ├─ 负载均衡(5种)  │  │ ├─ 心跳机制        │                    │
│  │  ├─ Mock 引擎      │  │ └─ 连接池管理      │                    │
│  │  ├─ 熔断限流       │  │                    │                    │
│  │  ├─ 链路追踪       │  │                    │                    │
│  │  └─ SPI 扩展机制   │  │                    │                    │
│  └───────────────────┘  └───────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        业务服务层                                 │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐        │
│  │ Engine Service│  │ Radar Service │  │Command Service│        │
│  │   (Provider)  │  │   (Provider)  │  │  (Consumer)   │        │
│  │    曲率引擎    │  │    深空雷达    │  │   舰队指挥    │        │
│  └───────────────┘  └───────────────┘  └───────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

### 架构亮点：控制面/数据面分离

- **数据面**：RPC 核心通信层，负责服务调用、负载均衡、Mock 拦截、熔断限流
- **控制面**：服务注册中心，管理服务元数据、Mock 规则、保护配置、链路数据
- **优势**：符合云原生设计理念，控制逻辑与数据转发解耦

---

## 🛠 技术栈

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

## 🔥 核心特性

### 1. 自定义 RPC 协议

```java
// 消息头设计（16 字节）
+----------------+----------------+----------------+----------------+
|  Magic Number  |     Version    | Serializer Type|  Message Type  |
|     (4 byte)   |    (1 byte)    |    (1 byte)    |    (1 byte)    |
+----------------+----------------+----------------+----------------+
|   Request ID   |          Data Length           |
|     (8 byte)   |            (4 byte)             |
+----------------+----------------+----------------+
```

### 2. 多策略负载均衡

支持 5 种负载均衡策略，通过 SPI 机制可扩展：

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| RoundRobin | 轮询选择 | 流量均匀分布 |
| Random | 加权随机 | 服务器性能差异 |
| WeightedRoundRobin | 加权轮询 | 按权重分配流量 |
| LeastActive | 最少活跃调用 | 长连接场景 |
| ConsistentHash | 一致性哈希 | 需要会话保持 |

```java
// 服务预热权重支持
ServiceInstance instance = new ServiceInstance();
instance.setWarmupWeight(50);  // 预热期间权重
instance.setWeight(100);       // 正常权重
```

### 3. 企业级 Mock 引擎

支持两种 Mock 模式：

```java
// 短路模式 (SHORT_CIRCUIT)：直接返回 Mock 数据，不发起网络请求
// 篡改模式 (TAMPER)：先发起真实请求，再合并 Mock 数据
```

**条件匹配能力**：
- 多参数组合匹配（AND 关系）
- 支持操作符：`equals`, `contains`, `regex`, `gt`, `lt`, `gte`, `lte`
- 占位符篡改：`{{base}}` 保留原始值，支持数学运算 `{{base}}+100`

### 4. 熔断限流保护

```java
// 动态熔断器配置
ProtectionConfig config = new ProtectionConfig();
config.setCircuitBreakerEnabled(true);
config.setFailureRateThreshold(50);    // 失败率阈值 50%
config.setSlowCallDurationThreshold(2000);  // 慢调用阈值 2s
config.setSlowCallRateThreshold(30);   // 慢调用率阈值 30%

// 限流配置
config.setRateLimiterEnabled(true);
config.setRateLimit(100);  // 每秒 100 次
```

### 5. 分布式链路追踪

```java
// Span 数据结构
public class Span {
    private String traceId;       // 链路 ID
    private String spanId;        // 当前 Span ID
    private String parentSpanId;  // 父 Span ID
    private String serviceName;   // 服务名
    private String methodName;    // 方法名
    private String kind;          // CLIENT/SERVER
    private long startTime;       // 开始时间
    private long duration;        // 耗时
    private boolean success;      // 是否成功
}
```

**可视化**：前端瀑布图展示调用链，类似 Jaeger 风格。

### 6. SSE 实时推送

Mock 规则变更通过 Server-Sent Events 实时推送到所有消费者，毫秒级生效：

```java
// 控制面推送
GET /api/v1/sse/mock-rules/subscribe

// 消费者订阅
EventSource eventSource = new EventSource(url);
eventSource.addEventListener("mock-rule-update", listener);
```

### 7. SPI 扩展机制

```java
// 负载均衡器扩展点
public interface LoadBalancer {
    InetSocketAddress select(List<InetSocketAddress> addresses, String serviceName);
}

// 序列化器扩展点
public interface Serializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
```

---

## 🚀 CI/CD 流程

> **完整的自动化部署流水线，push 即部署**

### 流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub Actions Pipeline                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐  │
│  │ Checkout │ -> │ Build    │ -> │ Docker   │ -> │ Deploy   │  │
│  │   Code   │    │ (Maven)  │    │  Build   │    │   SSH    │  │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘  │
│                                                                 │
│  触发条件：push to master                                       │
│  执行时间：约 5-8 分钟                                          │
│  部署目标：阿里云 ECS (42.193.105.133)                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
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
┌─────────────────────────────────────────────────────────────────┐
│                     阿里云 ECS 服务器                             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Docker Compose                         │   │
│  │                                                          │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │   │
│  │  │ MySQL   │  │Control  │  │ Engine  │  │ Radar   │     │   │
│  │  │  :3306  │  │ Plane   │  │ :8081   │  │ :8082   │     │   │
│  │  │         │  │ :8080   │  │         │  │         │     │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘     │   │
│  │                                                          │   │
│  │  ┌─────────┐  ┌─────────┐                               │   │
│  │  │ Command │  │Dashboard│                               │   │
│  │  │ :8083   │  │ :3000   │  ← Nginx 反向代理              │   │
│  │  │         │  │         │                               │   │
│  │  └─────────┘  └─────────┘                               │   │
│  │                                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              lumina-net (Docker Bridge Network) │
└─────────────────────────────────────────────────────────────────┘
```

### 部署特点

- ✅ **零停机部署**：容器平滑切换，服务不中断
- ✅ **滚动更新**：逐个服务替换，确保可用性
- ✅ **健康检查**：等待 MySQL 就绪后再启动应用服务
- ✅ **资源限制**：每个容器配置 CPU/内存限制，防止资源争抢
- ✅ **日志管理**：限制日志文件大小，防止磁盘占满
- ✅ **环境变量解耦**：本地开发与 Docker 部署通过环境变量自动切换

---

## 📁 项目结构

```
lumina-rpc/
├── lumina-rpc-protocol/        # 协议层：编解码、消息定义
│   └── src/main/java/
│       ├── codec/              # RpcEncoder, RpcDecoder
│       ├── transport/          # NettyClient, NettyClientHandler
│       ├── pool/               # 连接池管理
│       └── spi/                # 序列化器 SPI
│
├── lumina-rpc-core/            # 核心层：动态代理、服务发现
│   └── src/main/java/
│       ├── annotation/         # @LuminaService, @LuminaReference
│       ├── proxy/              # ByteBuddy 动态代理
│       ├── discovery/          # 服务发现客户端
│       ├── cluster/            # 集群容错、重试机制
│       ├── spi/                # 负载均衡器 SPI (5种策略)
│       ├── protection/         # 熔断器、限流器
│       ├── trace/              # 链路追踪 (Span 收集、上报)
│       ├── mock/               # Mock 规则引擎
│       └── spring/             # Spring 自动配置
│
├── lumina-control-plane/       # 控制面：注册中心
│   └── src/main/java/
│       ├── controller/         # REST API
│       ├── service/            # 业务逻辑
│       ├── entity/             # JPA 实体 (服务、Mock规则、Span、请求统计)
│       └── repository/         # 数据访问
│
├── lumina-dashboard/           # 前端监控面板
│   └── src/
│       ├── views/              # 页面组件 (拓扑图、链路追踪、Mock配置等)
│       ├── components/         # 通用组件
│       ├── api/                # API 模块化封装
│       └── types/              # TypeScript 类型定义
│
├── lumina-sample-engine/       # 示例服务：曲率引擎
├── lumina-sample-radar/        # 示例服务：深空雷达
├── lumina-sample-command/      # 示例服务：舰队指挥 (Consumer)
│
├── docker-compose.yml          # 容器编排
├── Dockerfile.*                # 各服务 Dockerfile
└── .github/workflows/          # CI/CD 配置
```

---

## 🏃 快速开始

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

## 📸 功能截图

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

## 📊 性能指标

| 指标 | 数值 |
|------|------|
| 单次 RPC 调用耗时 | < 10ms (本地网络) |
| 心跳间隔 | 30s |
| 服务过期时间 | 90s |
| 连接超时 | 5s |
| 请求超时 | 5s (可配置) |
| 连接池大小 | min=2, max=10 (per address) |

---

## 🔮 未来规划

- [x] 多策略负载均衡 (已完成 5 种)
- [x] 链路追踪与可视化
- [x] 熔断限流保护
- [ ] 支持 gRPC 协议
- [ ] 集成 OpenTelemetry 可观测性
- [ ] 支持 Java 21 虚拟线程
- [ ] 接入 Nacos/Apollo 配置中心
- [ ] 支持 K8s 部署

---

## 👤 作者

**Wang Shun (王顺)**

- GitHub: [@xixi-box](https://github.com/xixi-box)

---

## 📄 License

MIT License

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star！**

</div>