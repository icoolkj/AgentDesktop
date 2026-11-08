-- Agent Desktop — MySQL schema (v0.1)
-- 执行: mysql -u root -p agent_desktop < schema.sql

CREATE DATABASE IF NOT EXISTS agent_desktop
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE agent_desktop;

-- 聊天会话
CREATE TABLE IF NOT EXISTS chat_session (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '会话 ID',
    title       VARCHAR(255) NULL COMMENT '标题',
    workspace   VARCHAR(1024) NULL COMMENT '工作区路径',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 聊天会话';

-- 聊天消息
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL COMMENT '会话 ID',
    role        VARCHAR(16)  NOT NULL COMMENT 'user | assistant | system | tool',
    content     MEDIUMTEXT   NOT NULL,
    tools_json  JSON         NULL COMMENT 'Tool activity for assistant turns',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_created (session_id, created_at),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

-- MCP 服务配置（P3）
CREATE TABLE IF NOT EXISTS mcp_server_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128) NOT NULL UNIQUE,
    config_json JSON         NOT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP Server 配置';

-- 权限规则（P2）
CREATE TABLE IF NOT EXISTS permission_rule (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope       VARCHAR(32)  NOT NULL COMMENT 'global | workspace',
    scope_key   VARCHAR(1024) NULL COMMENT 'workspace 路径等',
    rule_json   JSON         NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scope (scope, scope_key(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限规则';

-- 审计日志（P2）
CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64)  NULL,
    tool_name   VARCHAR(128) NULL,
    action      VARCHAR(64)  NOT NULL,
    detail_json JSON         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用审计';

-- LLM 配置（单行 id=1，Settings 页持久化）
CREATE TABLE IF NOT EXISTS llm_settings (
    id              TINYINT      NOT NULL PRIMARY KEY DEFAULT 1,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    provider        VARCHAR(64)  NOT NULL DEFAULT 'openai-compatible',
    base_url        VARCHAR(512) NOT NULL DEFAULT 'https://api.openai.com/v1',
    api_key         VARCHAR(512) NULL COMMENT 'LLM API Key',
    model           VARCHAR(128) NOT NULL DEFAULT 'gpt-4o-mini',
    temperature     DOUBLE       NOT NULL DEFAULT 0.7,
    max_tokens      INT          NOT NULL DEFAULT 4096,
    timeout_seconds INT          NOT NULL DEFAULT 120,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 配置';

-- 通用 JSON 配置（features / hooks_global 等）
CREATE TABLE IF NOT EXISTS agent_config (
    config_key   VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '配置键',
    config_json  JSON         NOT NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 扩展配置';

-- permission_rule 唯一索引（scope + workspace）
ALTER TABLE permission_rule
    ADD UNIQUE KEY uk_permission_scope (scope, scope_key(512));
