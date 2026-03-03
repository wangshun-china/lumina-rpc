# Phase 4: 构建 Starfleet 演示集群 (微服务闭环演练)

## 1. 目标
构建一个具备极客色彩的“星际舰队指挥系统”微服务群，替代俗套的电商 Demo。
该集群包含三个节点：一个指挥官网关（Consumer），两个深空传感器节点（Provider）。通过模拟深空通信的高延迟和设备故障，完美展现 Lumina-RPC 的拓扑监控与动态 Mock 降级能力。

## 2. 节点 A: `lumina-sample-engine` (曲率引擎传感器 - Provider)
- **端口：** 8081
- **职责：** 提供星舰引擎的实时状态查询。
- **接口：** `EngineService.getWarpStatus(String shipId)`
- **核心逻辑与特效：**
  - 返回一个包含 `temperature` (温度) 和 `warpReady` (是否可跃迁) 的 DTO。
  - **模拟深空高延迟：** 方法内部强制 `Thread.sleep` 随机 200ms ~ 800ms。

## 3. 节点 B: `lumina-sample-radar` (深空雷达阵列 - Provider)
- **端口：** 8082
- **职责：** 提供周边敌对目标的扫描结果。
- **接口：** `RadarService.scanEnemies(String sector)`
- **核心逻辑与特效 (故障模拟)：**
  - 返回一个整数，表示扫描到的敌舰数量。
  - **模拟设备受损 (核心故障点)：** 这是一个极其不稳定的服务！请加入随机逻辑：有 **40% 的概率**直接抛出 `RuntimeException("雷达阵列受到离子风暴干扰，连接中断")`。有 60% 的概率正常返回随机数 0~5。

## 4. 节点 C: `lumina-sample-command` (舰队指挥网关 - Consumer/BFF)
- **端口：** 8083
- **职责：** 核心的业务网关，负责统筹数据并下达跃迁指令。
- **依赖：** 使用 `@LuminaReference` 注入 `EngineService` 和 `RadarService`。
- **自动流量引擎 (心跳遥测)：** 
  - 编写 `@Scheduled(fixedRate = 3000)` 定时任务，每 3 秒执行一次“例行扫描”。
  - 逻辑：先调用 `EngineService` 查引擎温度，再调用 `RadarService` 查敌情。
  - 打印充满极客风的日志：`[Command Center] 接收遥测数据 -> 引擎温度: xxx, 敌舰数量: xxx, 耗时: xxx ms`。（如果雷达报错，需 Catch 并打印严重的警告日志）。
- **外部 HTTP 接口：** 
  - 暴露 `GET /api/command/jump/{shipId}/{sector}`。
  - 逻辑：浏览器调用此接口时，内部并发/顺序请求上述两个 RPC。如果雷达报错，直接返回 HTTP 500 "跃迁中止：雷达数据丢失"。

## 5. 输出要求
请新建这三个 Spring Boot 子模块。确保它们的注册中心地址均指向 `http://localhost:8080`。确保依赖和包名完全正确，使其能够被独立启动。