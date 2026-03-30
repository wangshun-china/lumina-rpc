# Lumina-RPC

<div align="center">

**面向可观测性的轻量 RPC 框架**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Netty](https://img.shields.io/badge/Netty-4.1-blue.svg)](https://netty.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[在线演示](http://42.193.105.133:3000)

</div>

---

## 项目简介

**Lumina-RPC** 是一款包含核心通信 SDK、控制面与可视化监控面板的轻量 RPC 框架，采用控制面/数据面分离架构。

### 核心特性

- **自定义 RPC 协议** - 17 字节消息头，解决 TCP 粘包/半包问题
- **服务注册发现** - 自研控制面注册中心，心跳检测与故障剔除
- **动态代理** - ByteBuddy 生成代理类，透明化远程调用
- **负载均衡** - SPI 扩展，支持 5 种策略（RoundRobin、Random、WeightedRoundRobin、LeastActive、ConsistentHash）
- **服务预热** - 新实例逐步承接流量，避免冷启动
- **集群容错** - 4 种策略（Failover、Failfast、Failsafe、Forking）
- **Mock 引擎** - 短路/篡改两种模式，条件匹配，SSE 推送规则变更
- **熔断限流** - 滑动窗口熔断器、令牌桶限流器
- **链路追踪** - 分布式调用链追踪与瀑布图可视化
- **可视化面板** - Vue 3 服务拓扑图，实时监控

---

## 架构设计

```
┌─ 前端监控层 ─────────────────────────────┐
│  lumina-dashboard (Vue 3)               │
│  服务拓扑图 | Mock规则配置 | 链路追踪     │
└─────────────────────────────────────────┘
                    ↓
┌─ 控制面层 ───────────────────────────────┐
│  lumina-control-plane (Spring Boot)     │
│  服务注册中心 | Mock规则管理 | SSE推送    │
│  MySQL持久化 | 心跳检测 & 健康管理        │
└─────────────────────────────────────────┘
                    ↓
┌─ 数据面层 (RPC核心) ─────────────────────┐
│  lumina-rpc-core     lumina-rpc-protocol │
│  动态代理/服务发现   协议编解码/消息序列化 │
│  负载均衡/Mock引擎   心跳机制/连接池      │
│  熔断限流/链路追踪   SPI扩展机制          │
└─────────────────────────────────────────┘
                    ↓
┌─ 业务服务层 ─────────────────────────────┐
│  sample-engine | sample-radar | sample-command │
│  (Provider)    | (Provider)   | (Consumer)     │
└─────────────────────────────────────────┘
```

### 架构亮点：控制面/数据面分离

- **数据面**：RPC 核心通信层，负责服务调用、负载均衡、Mock 拦截、熔断限流
- **控制面**：服务注册中心，管理服务元数据、Mock 规则、保护配置、链路数据
- **优势**：控制逻辑与数据转发解耦，便于独立扩展和运维

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行时环境 |
| Spring Boot | 3.3.0 | 应用框架 |
| Netty | 4.1.100 | 网络通信 |
| ByteBuddy | 1.14.9 | 动态代理生成 |
| Jackson | 2.15.2 | JSON 序列化 |
| Spring Data JPA | 3.3.0 | 数据持久化 |
| MySQL | 8.0 | 数据库 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | ^3.4.0 | 前端框架 |
| Vue Flow | ^1.33.0 | 服务拓扑图 |
| ECharts | ^5.5.0 | 链路追踪瀑布图 |
| Element Plus | ^2.5.0 | UI 组件库 |

### 运维

| 技术 | 用途 |
|------|------|
| Docker | 容器化部署 |
| Docker Compose | 多容器编排 |
| GitHub Actions | CI/CD 自动化 |

---

## 核心特性详解

### 1. 自定义 RPC 协议

**协议头设计（17 字节）**

| 字段 | 长度 | 说明 |
|------|------|------|
| Magic Number | 2 bytes | `0x4C55` ("LU")，用于快速识别协议 |
| Version | 1 byte | 协议版本号 |
| Serializer Type | 1 byte | 序列化类型：JSON=0, KRYO=1 |
| Message Type | 1 byte | REQUEST=0, RESPONSE=1, HEARTBEAT=2 |
| Request ID | 8 bytes | 雪花算法生成的唯一 ID |
| Data Length | 4 bytes | Body 长度，解决 TCP 粘包问题 |

使用 Netty 的 `LengthFieldBasedFrameDecoder` 自动处理粘包/半包。

### 2. 多策略负载均衡

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| RoundRobin | 轮询选择 | 流量均匀分布 |
| Random | 加权随机 | 服务器性能差异 |
| WeightedRoundRobin | 加权轮询 | 按权重分配流量 |
| LeastActive | 最少活跃调用 | 长连接场景 |
| ConsistentHash | 一致性哈希 | 需要会话保持 |

通过 SPI 机制可扩展自定义负载均衡器。

### 3. 集群容错策略

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| Failover | 失败自动重试其他服务器 | 读操作、幂等写操作 |
| Failfast | 快速失败，立即报错 | 非幂等写操作 |
| Failsafe | 失败安全，忽略异常 | 非关键操作 |
| Forking | 并行调用多个服务器，任一成功即返回 | 实时性要求高的场景 |

### 4. Mock 引擎

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| SHORT_CIRCUIT | 短路模式：直接返回 Mock 数据，不发起请求 | 服务降级 |
| TAMPER | 篡改模式：发起真实请求后合并 Mock 数据 | 数据脱敏、字段篡改 |

条件匹配支持多参数组合，操作符包括 `equals`、`contains`、`regex`、`gt`、`lt` 等。

### 5. 熔断限流保护

**熔断器状态机**：CLOSED → OPEN → HALF_OPEN

- 滑动窗口统计成功/失败率
- 可配置失败率阈值、慢调用阈值、熔断等待时间

**令牌桶限流器**：可配置每秒请求数上限

### 6. 分布式链路追踪

- Trace ID 通过 ThreadLocal 传递，支持跨服务调用链
- Span 数据包含服务名、方法名、耗时、状态等
- 瀑布图可视化展示调用链

---

## 项目结构

```
lumina-rpc/
├── lumina-rpc-protocol/        # 协议层：编解码、消息定义、连接池
├── lumina-rpc-core/            # 核心层：动态代理、服务发现、负载均衡、Mock引擎
├── lumina-control-plane/       # 控制面：注册中心、Mock规则管理、SSE推送
├── lumina-dashboard/           # 前端监控面板：Vue 3 + Vue Flow
├── lumina-sample-engine/       # 示例服务：曲率引擎 (Provider)
├── lumina-sample-radar/        # 示例服务：深空雷达 (Provider)
├── lumina-sample-command/      # 示例服务：舰队指挥 (Consumer)
├── docker-compose.yml          # 容器编排
├── Dockerfile.*                # 各服务 Dockerfile
└── .github/workflows/          # CI/CD 配置
```

---

## 快速开始

### 前置条件

- JDK 21+
- Maven 3.8+
- Docker & Docker Compose（可选）

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
  -p 3306:3306 \
  mysql:8.0

# 4. 启动控制面
java -jar lumina-control-plane/target/lumina-control-plane-exec.jar

# 5. 启动服务提供者
java -jar lumina-sample-engine/target/lumina-sample-engine-exec.jar
java -jar lumina-sample-radar/target/lumina-sample-radar-exec.jar

# 6. 启动服务消费者
java -jar lumina-sample-command/target/lumina-sample-command-exec.jar

# 7. 启动前端
cd lumina-dashboard
pnpm install && pnpm dev
```

### Docker 部署

```bash
docker-compose up -d
docker-compose ps
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

## CI/CD 流程

push to master 自动触发部署：

1. **Checkout** → 检出代码
2. **Build** → Maven 构建后端，pnpm 构建前端
3. **Docker Build** → 构建并推送 5 个镜像到阿里云 ACR
4. **Deploy** → SSH 远程执行 docker-compose 部署

部署目标：阿里云 ECS (120.26.186.0)，执行时间约 5-8 分钟。

---

## 性能压测

### 测试环境

| 项目 | 配置 |
|------|------|
| OS | Windows 11 Pro |
| CPU | AMD Ryzen 9 7845HX |
| 内存 | 32 GB DDR5 5200MHz |
| JDK | 21 |
| 工具 | Apache JMeter 5.6.3 |

### 测试结果

| 线程数 | QPS | 平均响应 | P99 | 错误率 |
|--------|-----|----------|-----|--------|
| 500 | 6863 | 70ms | 671ms | 0% |
| 1000 | 6753 | 71ms | 1007ms | 0% |
| 2000 | 6811 | 70ms | 857ms | 0% |
| 5000 | 7185 | 66ms | 768ms | 0% |

**结论**：单机环境稳定支撑 6500-7000 QPS，平均响应 ~70ms，全程 0% 错误率。水平扩展 Provider 可提升吞吐量。

---

## 未来规划

- [x] 多策略负载均衡（已完成 5 种）
- [x] 链路追踪与可视化
- [x] 熔断限流保护
- [x] 集群容错策略
- [ ] 支持 gRPC 协议
- [ ] 集成 OpenTelemetry
- [ ] 支持 Java 21 虚拟线程
- [ ] 接入 Nacos/Apollo 配置中心

---

## 作者

**Wang Shun** - GitHub: [@xixi-box](https://github.com/xixi-box)

## License

MIT License

---

<div align="center">

如果这个项目对你有帮助，请给一个 Star ⭐

</div>