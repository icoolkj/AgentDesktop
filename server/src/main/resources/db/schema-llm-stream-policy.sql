-- Agent stream policy (align with CPA ToolTest.vue)
USE agent_desktop;

ALTER TABLE llm_settings
    ADD COLUMN IF NOT EXISTS native_tool_calls_only TINYINT(1) NOT NULL DEFAULT 1
        COMMENT 'Only API tool_calls, no DSML parse' AFTER timeout_seconds,
    ADD COLUMN IF NOT EXISTS stream_tool_turns TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'Stream tool planning turns (tools=auto)' AFTER native_tool_calls_only,
    ADD COLUMN IF NOT EXISTS stream_answer_turns TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'Stream final answer (tool_choice=none); CPA round 2 uses false' AFTER stream_tool_turns;
