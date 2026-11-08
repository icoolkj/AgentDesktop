package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.LlmSettingsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmSettingsMapper {

    LlmSettingsRow findById(@Param("id") int id);

    int upsert(LlmSettingsRow row);
}
