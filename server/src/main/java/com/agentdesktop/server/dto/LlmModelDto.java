package com.agentdesktop.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmModelDto {

    private String id;
    private String ownedBy;

    public LlmModelDto() {
    }

    public LlmModelDto(String id, String ownedBy) {
        this.id = id;
        this.ownedBy = ownedBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }
}
