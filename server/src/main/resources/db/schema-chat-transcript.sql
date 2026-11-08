-- Chat transcript: persist tool activity per assistant turn (Claude Code JSONL parity)
USE agent_desktop;

ALTER TABLE chat_message
    ADD COLUMN tools_json JSON NULL COMMENT 'Tool calls/results for assistant turns' AFTER content;
