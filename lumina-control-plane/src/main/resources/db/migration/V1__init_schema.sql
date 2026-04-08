-- ============================================================
-- Lumina-RPC 初始化数据库脚本
-- Flyway 版本管理
-- ============================================================

-- 1. 服务实例表
CREATE TABLE lumina_service_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    instance_id VARCHAR(255) NOT NULL UNIQUE COMMENT '实例ID',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    status VARCHAR(50) NOT NULL DEFAULT 'UP' COMMENT '状态: UP/DOWN',
    version VARCHAR(50) COMMENT '服务版本',
    metadata TEXT COMMENT '实例元数据',
    service_metadata TEXT COMMENT '服务元数据(接口方法信息)',
    start_time BIGINT COMMENT '启动时间戳(毫秒)',
    warmup_period BIGINT DEFAULT 60000 COMMENT '预热时间(毫秒)',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    registered_at DATETIME NOT NULL COMMENT '注册时间',
    expires_at DATETIME COMMENT '过期时间',
    INDEX idx_service_name (service_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务实例表';

-- 2. Mock 规则表
CREATE TABLE lumina_mock_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    method_name VARCHAR(255) NOT NULL COMMENT '方法名称',
    match_type VARCHAR(50) DEFAULT 'exact' COMMENT '匹配类型',
    condition_rule TEXT COMMENT '条件规则(JSON)',
    mock_type VARCHAR(50) DEFAULT 'SHORT_CIRCUIT' COMMENT 'Mock类型: SHORT_CIRCUIT/TAMPER',
    match_condition VARCHAR(1000) COMMENT '匹配条件',
    response_type VARCHAR(50) NOT NULL DEFAULT 'success' COMMENT '响应类型',
    response_body TEXT NOT NULL COMMENT '响应体',
    response_delay_ms INT DEFAULT 0 COMMENT '延迟(毫秒)',
    http_status INT DEFAULT 200 COMMENT 'HTTP状态码',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
    description VARCHAR(500) COMMENT '描述',
    tags VARCHAR(255) COMMENT '标签',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    created_by VARCHAR(100) COMMENT '创建人',
    updated_by VARCHAR(100) COMMENT '更新人',
    INDEX idx_service_method (service_name, method_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mock规则表';

-- 3. 保护配置表
CREATE TABLE lumina_protection_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL UNIQUE COMMENT '服务名称',
    -- 熔断器配置
    circuit_breaker_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用熔断器',
    circuit_breaker_threshold INT DEFAULT 50 COMMENT '熔断器错误率阈值(%)',
    circuit_breaker_timeout BIGINT DEFAULT 30000 COMMENT '熔断器恢复时间(毫秒)',
    circuit_breaker_window_size INT DEFAULT 100 COMMENT '滑动窗口大小',
    -- 限流器配置
    rate_limiter_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用限流器',
    rate_limiter_permits INT DEFAULT 100 COMMENT '限流阈值(QPS)',
    -- 集群配置
    cluster_strategy VARCHAR(50) DEFAULT 'failover' COMMENT '集群策略',
    retries INT DEFAULT 3 COMMENT '重试次数',
    timeout_ms BIGINT DEFAULT 0 COMMENT '超时时间(毫秒)',
    -- 元数据
    version BIGINT DEFAULT 1 COMMENT '版本号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    description VARCHAR(500) COMMENT '描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保护配置表';

-- 4. 请求统计表
CREATE TABLE lumina_request_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    stat_time DATETIME NOT NULL COMMENT '统计时间(分钟粒度)',
    total_requests BIGINT NOT NULL DEFAULT 0 COMMENT '总请求数',
    success_count BIGINT NOT NULL DEFAULT 0 COMMENT '成功请求数',
    fail_count BIGINT NOT NULL DEFAULT 0 COMMENT '失败请求数',
    total_latency BIGINT DEFAULT 0 COMMENT '总响应时间(毫秒)',
    max_latency BIGINT DEFAULT 0 COMMENT '最大响应时间(毫秒)',
    min_latency BIGINT DEFAULT 0 COMMENT '最小响应时间(毫秒)',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_service_time (service_name, stat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求统计表';

-- 5. Span 链路追踪表
CREATE TABLE lumina_span (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL COMMENT 'Trace ID',
    span_id VARCHAR(64) NOT NULL COMMENT 'Span ID',
    parent_span_id VARCHAR(64) COMMENT '父Span ID',
    service_name VARCHAR(255) NOT NULL COMMENT '服务名称',
    method_name VARCHAR(255) COMMENT '方法名称',
    kind VARCHAR(16) COMMENT 'Span类型: CLIENT/SERVER',
    start_time BIGINT COMMENT '开始时间戳(毫秒)',
    duration BIGINT COMMENT '耗时(毫秒)',
    success BOOLEAN COMMENT '是否成功',
    error_message VARCHAR(1024) COMMENT '错误信息',
    remote_address VARCHAR(128) COMMENT '远程地址',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_service_name (service_name),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链路追踪Span表';