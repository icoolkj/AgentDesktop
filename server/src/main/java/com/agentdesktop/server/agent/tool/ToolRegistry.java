package com.agentdesktop.server.agent.tool;

import com.agentdesktop.server.agent.extension.SkillAllowedToolFilter;
import com.agentdesktop.server.agent.extension.SubagentTypes;
import com.agentdesktop.server.agent.mcp.McpToolBridge;
import com.agentdesktop.server.agent.llm.LlmToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName;
    private final McpToolBridge mcpToolBridge;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<Tool> tools, McpToolBridge mcpToolBridge, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.mcpToolBridge = mcpToolBridge;
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : tools) {
            map.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(map);
    }

    public List<LlmToolDefinition> definitions(ToolContext context) {
        if (!context.hasWorkspace()) {
            return List.of();
        }
        boolean planMode = "plan".equals(context.permissionModeOrDefault());
        boolean shellSubagent = SubagentTypes.SHELL.equals(context.subagentTypeOrDefault());
        List<String> allowedTools = context.activeSkillAllowedToolsOrEmpty();
        List<LlmToolDefinition> defs = new ArrayList<>();
        for (Tool tool : toolsByName.values()) {
            if (!tool.available(context)) {
                continue;
            }
            if (!SkillAllowedToolFilter.isAllowed(tool, allowedTools)) {
                continue;
            }
            if (planMode && !tool.concurrencySafe()) {
                continue;
            }
            if (shellSubagent && !isShellSubagentTool(tool)) {
                continue;
            }
            defs.add(tool.definition());
        }
        for (LlmToolDefinition mcpDef : mcpToolBridge.definitions(context)) {
            if (SkillAllowedToolFilter.isAllowedMcpTool(mcpDef.name(), allowedTools)) {
                defs.add(mcpDef);
            }
        }
        return defs;
    }

    public boolean isConcurrencySafe(String name) {
        Tool tool = toolsByName.get(name);
        return tool != null && tool.concurrencySafe();
    }

    public ToolResult execute(String name, String argumentsJson, ToolContext context) {
        if (mcpToolBridge.isMcpTool(name)) {
            return mcpToolBridge.execute(name, argumentsJson, context);
        }
        Tool tool = toolsByName.get(name);
        if (tool == null) {
            return ToolResult.failure("Unknown tool: " + name);
        }
        try {
            return tool.execute(argumentsJson == null ? "{}" : argumentsJson, context);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    public JsonNode parseArguments(String argumentsJson) throws Exception {
        return objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
    }

    private static boolean isShellSubagentTool(Tool tool) {
        if ("run_terminal_cmd".equals(tool.name())) {
            return true;
        }
        if ("Task".equals(tool.name()) || "WebSearch".equals(tool.name()) || "WebFetch".equals(tool.name())) {
            return false;
        }
        return tool.concurrencySafe();
    }
}
