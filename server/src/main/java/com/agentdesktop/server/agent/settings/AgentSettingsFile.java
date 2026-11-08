package com.agentdesktop.server.agent.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentSettingsFile {

    private StoredLlmSettings llm = new StoredLlmSettings();

    public StoredLlmSettings getLlm() {
        return llm;
    }

    public void setLlm(StoredLlmSettings llm) {
        this.llm = llm != null ? llm : new StoredLlmSettings();
    }
}
