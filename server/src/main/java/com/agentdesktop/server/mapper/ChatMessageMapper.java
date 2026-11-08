package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insert(ChatMessage message);

    List<ChatMessage> listBySessionId(@Param("sessionId") String sessionId);
}
