package com.agentdesktop.server.dto;

public class SkillInstallResultDto {

    private boolean ok;
    private String message;
    private String name;
    private String installedPath;

    public static SkillInstallResultDto success(String name, String installedPath) {
        SkillInstallResultDto dto = new SkillInstallResultDto();
        dto.ok = true;
        dto.message = "Skill installed";
        dto.name = name;
        dto.installedPath = installedPath;
        return dto;
    }

    public static SkillInstallResultDto failure(String message) {
        SkillInstallResultDto dto = new SkillInstallResultDto();
        dto.ok = false;
        dto.message = message;
        return dto;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstalledPath() {
        return installedPath;
    }

    public void setInstalledPath(String installedPath) {
        this.installedPath = installedPath;
    }
}
