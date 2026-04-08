-- ============================================
-- Lumina-RPC 服务名迁移脚本
-- 从旧包名 com.lumina.sample.*.service 迁移到新包名 com.lumina.sample.*.api
-- ============================================

-- 更新 Mock 规则表中的服务名
UPDATE lumina_mock_rule
SET service_name = REPLACE(service_name, 'com.lumina.sample.engine.service.', 'com.lumina.sample.engine.api.')
WHERE service_name LIKE 'com.lumina.sample.engine.service.%';

UPDATE lumina_mock_rule
SET service_name = REPLACE(service_name, 'com.lumina.sample.radar.service.', 'com.lumina.sample.radar.api.')
WHERE service_name LIKE 'com.lumina.sample.radar.service.%';

-- 更新保护配置表中的服务名
UPDATE lumina_protection_config
SET service_name = REPLACE(service_name, 'com.lumina.sample.engine.service.', 'com.lumina.sample.engine.api.')
WHERE service_name LIKE 'com.lumina.sample.engine.service.%';

UPDATE lumina_protection_config
SET service_name = REPLACE(service_name, 'com.lumina.sample.radar.service.', 'com.lumina.sample.radar.api.')
WHERE service_name LIKE 'com.lumina.sample.radar.service.%';

-- 验证更新结果（可选，执行后可查看更新的记录）
-- SELECT * FROM lumina_mock_rule WHERE service_name LIKE 'com.lumina.sample.%.api.%';
-- SELECT * FROM lumina_protection_config WHERE service_name LIKE 'com.lumina.sample.%.api.%';
