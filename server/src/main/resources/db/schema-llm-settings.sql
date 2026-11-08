-- v0.2: LLM settings in MySQL
USE agent_desktop;

CREATE TABLE IF NOT EXISTS llm_settings (
    id              TINYINT      NOT NULL PRIMARY KEY DEFAULT 1,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    provider        VARCHAR(64)  NOT NULL DEFAULT 'openai-compatible',
    base_url        VARCHAR(512) NOT NULL DEFAULT 'https://api.openai.com/v1',
    api_key         VARCHAR(512) NULL COMMENT 'LLM API Key (server-side only)',
    model           VARCHAR(128) NOT NULL DEFAULT 'gpt-4o-mini',
    temperature     DOUBLE       NOT NULL DEFAULT 0.7,
    max_tokens      INT          NOT NULL DEFAULT 4096,
    timeout_seconds INT          NOT NULL DEFAULT 120,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 配置（单行 id=1）';
