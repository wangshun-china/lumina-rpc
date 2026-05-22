# Lumina-RPC: 面向可观测性的企业级 RPC 框架 (全局总纲)

## 1. 产品愿景
Lumina-RPC 是一款包含“核心通信 SDK”、“云端控制面”与“可视化拓扑大盘”的轻量级微服务治理框架。
**核心原则：纯粹的内部 RPC 与服务治理，绝对不包含对外的 API 网关（Gateway）业务。外部流量由业务层的 Consumer (BFF 模式) 自行处理。**

## 2. 系统模块划分 (Maven Multi-Module)
项目必须采用标准 Maven 父子工程结构：
1. `lumina-rpc-core`: 核心数据面 SDK（底层的 Netty 通信、动态代理、SPI 机制）。
2. `lumina-control-plane`: 控制面（Spring Boot 注册中心与 Mock 规则下发服务）。
3. `lumina-dashboard`: 独立的前端管控面板（Vue 3 + Vue Flow）。
4. `lumina-sample-provider`: 业务演示节点（提供者）。
5. `lumina-sample-consumer`: 业务演示节点（消费者 / BFF 网关）。

## 3. 全局数据库规范 (Zeabur 生产环境)
控制面必须连接至以下 MySQL 数据库持久化数据：
- **Host:** `${LUMINA_RPC_ZEABUR_MYSQL_HOST}`
- **Port:** 29418
- **Database:** zeabur
- **User:** `${LUMINA_RPC_ZEABUR_MYSQL_USERNAME}`
- **Password:** `${LUMINA_RPC_ZEABUR_MYSQL_PASSWORD}`

核心表结构要求（请在控制面使用 Spring Data JPA 自动生成，或提供 SQL 脚本）：
- `lumina_service_instance`: 记录微服务节点 (id, service_name, ip, port, status, last_heartbeat)。
- `lumina_mock_rule`: 记录动态降级规则 (id, service_name, method_name, mock_response_json, is_active)。

## 4. Claude Code 纪律要求
你是一个严谨的高级架构师。你必须等待我下发具体的 Phase 文档后，才可编写对应模块的代码。绝对不允许跨模块提前生成代码。
