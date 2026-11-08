package com.agentdesktop.server.dto;

import java.util.ArrayList;
import java.util.List;

public class McpTestResultDto {

    private boolean ok;
    private String message;
    private List<McpToolDto> tools = new ArrayList<>();

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

    public List<McpToolDto> getTools() {
        return tools;
    }

    public void setTools(List<McpToolDto> tools) {
        this.tools = tools;
    }
}
