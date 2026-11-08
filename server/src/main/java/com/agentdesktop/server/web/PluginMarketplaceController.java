package com.agentdesktop.server.web;

import com.agentdesktop.server.agent.extension.PluginMarketplaceService;
import com.agentdesktop.server.dto.PluginInstallResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/plugins/marketplace")
public class PluginMarketplaceController {

    private final PluginMarketplaceService marketplaceService;

    public PluginMarketplaceController(PluginMarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @GetMapping
    public Map<String, Object> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String workspaceRoot) {
        return marketplaceService.listMarketplace(workspaceRoot);
    }

    @PostMapping("/install")
    public PluginInstallResultDto install(@RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        String scope = body == null ? "user" : body.getOrDefault("scope", "user");
        String workspaceRoot = body == null ? null : body.get("workspaceRoot");
        String installMethod = body == null ? "github" : body.getOrDefault("installMethod", "github");
        PluginInstallResultDto result = marketplaceService.installPlugin(name, scope, workspaceRoot, installMethod);
        if (!result.isOk()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
        }
        return result;
    }
}
