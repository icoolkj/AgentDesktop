package com.agentdesktop.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmModelsResponseDto {

    private List<LlmModelDto> models = new ArrayList<>();
    private String defaultModel;
    private String error;

    public List<LlmModelDto> getModels() {
        return models;
    }

    public void setModels(List<LlmModelDto> models) {
        this.models = models != null ? models : new ArrayList<>();
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
