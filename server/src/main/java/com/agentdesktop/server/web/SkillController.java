package com.agentdesktop.server.web;

import com.agentdesktop.server.agent.extension.SkillInstallService;
import com.agentdesktop.server.dto.SkillCatalogInstallRequestDto;
import com.agentdesktop.server.dto.SkillInstallRequestDto;
import com.agentdesktop.server.dto.SkillInstallResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillInstallService skillInstallService;

    public SkillController(SkillInstallService skillInstallService) {
        this.skillInstallService = skillInstallService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String workspaceRoot) {
        List<Map<String, Object>> catalog = skillInstallService.listCatalog();
        List<Map<String, Object>> skills = skillInstallService.listInstalledSkills(workspaceRoot);
        for (Map<String, Object> entry : catalog) {
            String id = String.valueOf(entry.get("id"));
            boolean installedOnDisk = skills.stream().anyMatch(skill -> {
                Object path = skill.get("installPath");
                return id.equals(skill.get("name"))
                        && path != null
                        && !String.valueOf(path).startsWith("bundled:");
            });
            boolean bundledAvailable = skills.stream().anyMatch(skill ->
                    id.equals(skill.get("name")) && "bundled".equals(skill.get("source")));
            entry.put("installed", installedOnDisk);
            entry.put("bundledAvailable", bundledAvailable);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", skills);
        result.put("skillsLoaded", !skills.isEmpty());
        result.put("catalog", catalog);
        result.put("skillDirs", skillInstallService.skillDirs(workspaceRoot));
        result.put("usage", Map.of(
                "invokeTool", "Skill",
                "slash", "/skill-name",
                "listing", "Metadata injected each agent turn; body loads on invoke"));
        return result;
    }

    @PostMapping("/install")
    public SkillInstallResultDto install(@RequestBody SkillInstallRequestDto body) {
        SkillInstallResultDto result = skillInstallService.install(body);
        if (!result.isOk()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
        }
        return result;
    }

    @PostMapping("/catalog/install")
    public SkillInstallResultDto installFromCatalog(@RequestBody SkillCatalogInstallRequestDto body) {
        SkillInstallResultDto result = skillInstallService.installFromCatalog(body);
        if (!result.isOk()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
        }
        return result;
    }

    @DeleteMapping
    public SkillInstallResultDto uninstall(
            @RequestParam String name,
            @RequestParam String scope,
            @RequestParam(required = false) String workspaceRoot) {
        try {
            SkillInstallResultDto result = skillInstallService.uninstall(name, scope, workspaceRoot);
            if (!result.isOk()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMessage());
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
