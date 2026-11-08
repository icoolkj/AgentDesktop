-- Migration: agent_config + permission_rule unique index
USE agent_desktop;

CREATE TABLE IF NOT EXISTS agent_config (
    config_key   VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '配置键',
    config_json  JSON         NOT NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 扩展配置';

-- Safe add unique index (ignore if exists on re-run)
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'permission_rule'
      AND index_name = 'uk_permission_scope'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE permission_rule ADD UNIQUE KEY uk_permission_scope (scope, scope_key(512))',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
