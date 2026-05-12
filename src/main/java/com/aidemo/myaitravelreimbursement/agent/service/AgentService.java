package com.aidemo.myaitravelreimbursement.agent.service;

import com.aidemo.myaitravelreimbursement.agent.dto.AgentMessageDTO;
import com.aidemo.myaitravelreimbursement.agent.dto.AgentSessionVO;

import java.util.List;

/**
 * Agent 会话服务接口
 */
public interface AgentService {

    /**
     * 新建会话（仅插入一条占位记录，lastMessage="新建对话"）
     */
    String createSession(Long projectId);

    /**
     * 创建/记录一条用户对话（插入新行）
     */
    void createSession(Long projectId, String sessionId, String userMessage);

    /**
     * 保存 AI 回复（插入新行，role=assistant）
     */
    void saveAssistantMessage(String sessionId, String content);

    /**
     * 获取会话列表（每个sessionId返回一条，lastMessage取最新那条）
     */
    List<AgentSessionVO> listSessions(Long projectId);

    /**
     * 获取某个sessionId的所有对话记录（按id升序，即时间正序）
     */
    List<AgentMessageDTO> getSessionDetail(String sessionId);

    /**
     * 删除某个sessionId下的所有对话记录
     */
    void deleteSession(String sessionId);
}
