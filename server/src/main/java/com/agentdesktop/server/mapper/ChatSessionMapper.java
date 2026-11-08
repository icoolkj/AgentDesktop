package com.agentdesktop.server.mapper;

import com.agentdesktop.server.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    ChatSession findById(@Param("id") String id);

    int insert(ChatSession session);

    int touchUpdatedAt(@Param("id") String id);

    List<ChatSession> listRecent(@Param("limit") int limit, @Param("workspace") String workspace);

    int updateTitleIfDefault(@Param("id") String id, @Param("title") String title);

    int updateWorkspace(@Param("id") String id, @Param("workspace") String workspace);
}
