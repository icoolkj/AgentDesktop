package com.agentdesktop.server.dto;

public class PluginInstallResultDto {

    private boolean ok;
    private String message;
    private String name;
    private String version;
    private String installedPath;

    public static PluginInstallResultDto success(String name, String version, String installedPath) {
        PluginInstallResultDto dto = new PluginInstallResultDto();
        dto.ok = true;
        dto.message = "Plugin installed";
        dto.name = name;
        dto.version = version;
        dto.installedPath = installedPath;
        return dto;
    }

    public static PluginInstallResultDto failure(String message) {
        PluginInstallResultDto dto = new PluginInstallResultDto();
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getInstalledPath() {
        return installedPath;
    }

    public void setInstalledPath(String installedPath) {
        this.installedPath = installedPath;
    }
}
