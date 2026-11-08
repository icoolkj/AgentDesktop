package com.agentdesktop.server.dto;

import com.agentdesktop.server.agent.extension.HookDefinition;

import java.util.ArrayList;
import java.util.List;

public class HooksSettingsDto {

    private List<HookDefinition> hooks = new ArrayList<>();
    private String storage;

    public List<HookDefinition> getHooks() {
        return hooks;
    }

    public void setHooks(List<HookDefinition> hooks) {
        this.hooks = hooks == null ? new ArrayList<>() : hooks;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }
}
