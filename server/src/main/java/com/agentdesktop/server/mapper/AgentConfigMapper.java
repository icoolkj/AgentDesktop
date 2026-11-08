package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.AgentConfigRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentConfigMapper {

    AgentConfigRow findByKey(@Param("configKey") String configKey);

    int upsert(AgentConfigRow row);
}
