package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.PermissionRuleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionRuleMapper {

    List<PermissionRuleRow> findByScope(@Param("scope") String scope, @Param("scopeKey") String scopeKey);

    int upsert(PermissionRuleRow row);
}
