package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmResponse;
import com.agentdesktop.server.agent.llm.LlmToolCall;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves tool calls from LLM responses — Claude Code {@code query.ts} parity.
 * <p>
 * Only native OpenAI {@code message.tool_calls} from the gateway (CLIProxyAPI-Java).
 * Loop continuation ({@code needsFollowUp}) is {@code !toolCalls.isEmpty()} only.
 * Agent Desktop does not parse tool markup from {@code content}.
 */
@Component
public class ToolCallResolver {

    public List<LlmToolCall> resolve(LlmResponse response) {
        if (response.hasToolCalls()) {
            return response.toolCalls();
        }
        return List.of();
    }

    /**
     * Gateway returned internal tool markup in {@code content} without API {@code tool_calls}.
     * Fix CPA — do not parse markup into executable tools.
     */
    public boolean hasUnparsedToolMarkup(LlmResponse response) {
        if (response.hasToolCalls()) {
            return false;
        }
        return AssistantContentFilter.containsInternalMarkup(response.content());
    }
}
