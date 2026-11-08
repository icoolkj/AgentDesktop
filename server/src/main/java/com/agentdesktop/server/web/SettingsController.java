package com.agentdesktop.server.web;

import com.agentdesktop.server.agent.settings.ExtensionSettingsService;
import com.agentdesktop.server.agent.settings.HooksSettingsService;
import com.agentdesktop.server.agent.settings.LlmSettingsService;
import com.agentdesktop.server.agent.settings.McpSettingsService;
import com.agentdesktop.server.dto.FeatureSettingsDto;
import com.agentdesktop.server.dto.HooksSettingsDto;
import com.agentdesktop.server.dto.LlmModelsResponseDto;
import com.agentdesktop.server.dto.LlmSettingsDto;
import com.agentdesktop.server.dto.McpSettingsDto;
import com.agentdesktop.server.dto.McpTestResultDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final LlmSettingsService llmSettingsService;
    private final McpSettingsService mcpSettingsService;
    private final ExtensionSettingsService extensionSettingsService;
    private final HooksSettingsService hooksSettingsService;

    public SettingsController(
            LlmSettingsService llmSettingsService,
            McpSettingsService mcpSettingsService,
            ExtensionSettingsService extensionSettingsService,
            HooksSettingsService hooksSettingsService) {
        this.llmSettingsService = llmSettingsService;
        this.mcpSettingsService = mcpSettingsService;
        this.extensionSettingsService = extensionSettingsService;
        this.hooksSettingsService = hooksSettingsService;
    }

    @GetMapping("/llm")
    public LlmSettingsDto getLlmSettings() {
        return llmSettingsService.getPublicSettings();
    }

    @PutMapping("/llm")
    public LlmSettingsDto updateLlmSettings(@RequestBody LlmSettingsDto body) {
        return llmSettingsService.updateSettings(body);
    }

    @GetMapping("/llm/models")
    public LlmModelsResponseDto listLlmModels() {
        return llmSettingsService.listAvailableModels();
    }

    @GetMapping("/mcp")
    public McpSettingsDto getMcpSettings() {
        return mcpSettingsService.getSettings();
    }

    @PutMapping("/mcp")
    public McpSettingsDto updateMcpSettings(@RequestBody McpSettingsDto body) throws Exception {
        return mcpSettingsService.updateSettings(body);
    }

    @PostMapping("/mcp/test/{serverName}")
    public McpTestResultDto testMcpServer(@PathVariable String serverName) {
        return mcpSettingsService.testServer(serverName);
    }

    @PostMapping("/mcp/reload")
    public McpSettingsDto reloadMcp() {
        return mcpSettingsService.reload();
    }

    @GetMapping("/features")
    public FeatureSettingsDto getFeatureSettings() {
        return extensionSettingsService.getFeatures();
    }

    @PutMapping("/features")
    public FeatureSettingsDto updateFeatureSettings(@RequestBody FeatureSettingsDto body) {
        return extensionSettingsService.updateFeatures(body);
    }

    @GetMapping("/hooks")
    public HooksSettingsDto getHookSettings() {
        return hooksSettingsService.getHooks();
    }

    @PutMapping("/hooks")
    public HooksSettingsDto updateHookSettings(@RequestBody HooksSettingsDto body) {
        return hooksSettingsService.updateHooks(body);
    }
}
