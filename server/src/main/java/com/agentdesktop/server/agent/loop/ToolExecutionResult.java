package com.agentdesktop.server.agent.loop;

import com.agentdesktop.server.agent.llm.LlmToolCall;
import com.agentdesktop.server.agent.tool.ToolResult;

record ToolExecutionResult(LlmToolCall call, ToolResult result) {
}
