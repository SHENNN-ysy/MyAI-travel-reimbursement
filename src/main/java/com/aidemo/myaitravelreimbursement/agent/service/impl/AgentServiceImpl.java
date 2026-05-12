package com.aidemo.myaitravelreimbursement.agent.service.impl;

import com.aidemo.myaitravelreimbursement.agent.dto.AgentMessageDTO;
import com.aidemo.myaitravelreimbursement.agent.dto.AgentSessionVO;
import com.aidemo.myaitravelreimbursement.agent.service.AgentService;
import com.aidemo.myaitravelreimbursement.entity.AgentSession;
import com.aidemo.myaitravelreimbursement.mapper.AgentSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 会话服务实现
 * 核心设计：每条用户对话在 t_agent_session 中插入一行，sessionId 分组，id 升序 = 时间正序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentSessionMapper sessionMapper;

    @Override
    public String createSession(Long projectId) {
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        AgentSession record = new AgentSession();
        record.setProjectId(projectId);
        record.setSessionId(sessionId);
        record.setRole("user");
        record.setLastMessage("新建对话");
        record.setStatus(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(record);
        log.info("新建会话: sessionId={}, projectId={}", sessionId, projectId);
        return sessionId;
    }

    @Override
    @Transactional
    public void createSession(Long projectId, String sessionId, String userMessage) {
        AgentSession record = new AgentSession();
        record.setProjectId(projectId);
        record.setSessionId(sessionId);
        record.setRole("user");
        record.setLastMessage(userMessage);
        record.setStatus(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(record);
        log.info("记录用户对话: sessionId={}, projectId={}", sessionId, projectId);
    }

    @Override
    @Transactional
    public void saveAssistantMessage(String sessionId, String content) {
        // 查找同一 sessionId 下已有记录的 projectId
        AgentSession existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getSessionId, sessionId)
                        .last("LIMIT 1")
        );

        AgentSession record = new AgentSession();
        record.setProjectId(existing != null ? existing.getProjectId() : 0L);
        record.setSessionId(sessionId);
        record.setRole("assistant");
        record.setLastMessage(content);
        record.setStatus(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(record);
        log.debug("记录AI回复: sessionId={}, length={}", sessionId, content.length());
    }

    @Override
    public List<AgentSessionVO> listSessions(Long projectId) {
        // 每个 sessionId 只取最新一条（id 最大的），用于展示会话列表
        List<AgentSession> records = sessionMapper.selectList(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getProjectId, projectId)
                        .orderByDesc(AgentSession::getId)
        );

        // 按 sessionId 分组，每组只取 id 最大的那条
        return records.stream()
                .collect(Collectors.groupingBy(AgentSession::getSessionId))
                .values()
                .stream()
                .map(group -> {
                    AgentSession latest = group.stream()
                            .max((a, b) -> Long.compare(a.getId(), b.getId()))
                            .orElse(group.get(0));
                    return toSessionVO(latest);
                })
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentMessageDTO> getSessionDetail(String sessionId) {
        // 按 id 升序查询 = 时间正序
        List<AgentSession> records = sessionMapper.selectList(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getSessionId, sessionId)
                        .orderByAsc(AgentSession::getId)
        );

        return records.stream()
                .map(r -> AgentMessageDTO.builder()
                        .role(r.getRole())
                        .content(r.getLastMessage())
                        .timestamp(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        sessionMapper.delete(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getSessionId, sessionId)
        );
        log.info("删除会话: sessionId={}", sessionId);
    }

    private AgentSessionVO toSessionVO(AgentSession record) {
        return AgentSessionVO.builder()
                .id(record.getId())
                .sessionId(record.getSessionId())
                .projectId(record.getProjectId())
                .status(record.getStatus())
                .statusName(record.getStatus() == 0 ? "活跃" : "已完成")
                .lastMessage(record.getLastMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
