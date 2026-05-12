package com.aidemo.myaitravelreimbursement.agent.memory;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 对话记忆管理器
 * 按 sessionId 维护独立的 ChatMemory，每个会话独立保存对话历史
 */
@Component
public class AgentChatMemoryManager {

    private final Map<String, MessageWindowChatMemory> memories = new ConcurrentHashMap<>();

    private static final int MAX_MESSAGES = 3;

    public MessageWindowChatMemory getMemory(String sessionId) {
        return memories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(MAX_MESSAGES)
                        .build());
    }

    public void removeMemory(String sessionId) {
        memories.remove(sessionId);
    }

    public boolean hasMemory(String sessionId) {
        return memories.containsKey(sessionId);
    }
}
