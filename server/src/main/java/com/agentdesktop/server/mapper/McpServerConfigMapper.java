package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.McpServerConfigRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface McpServerConfigMapper {

    List<McpServerConfigRow> findAll();

    int upsert(McpServerConfigRow row);

    int deleteByName(@Param("name") String name);

    int deleteAll();
}
