package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.memory.AgentChatMemoryManager;
import com.aidemo.myaitravelreimbursement.agent.prompt.AgentSystemPrompt;
import com.aidemo.myaitravelreimbursement.agent.tools.FileTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ProjectTools;
import com.aidemo.myaitravelreimbursement.agent.tools.RecognitionTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ReportTools;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.skills.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Agent 对话服务（基于 LangChain4j AiServices）
 * 定义 Agent 行为接口，由 AiServices 自动生成实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
public class ReimbursementAgent {

    private final StreamingChatModel chatStreamModel;
    private final AgentChatMemoryManager memoryManager;
    private final ProjectTools projectTools;
    private final FileTools fileTools;
    private final RecognitionTools recognitionTools;
    private final ReportTools reportTools;

    /**
     * Agent 内部接口 — 定义 AI 助手的对话行为
     * AiServices 自动生成代理实现，处理 Tool Calling 和 ChatMemory
     */
    public interface Assistant {

        //@SystemMessage(AgentSystemPrompt.SYSTEM_PROMPT)
        TokenStream chatStream(String userMessage);
    }


    /**
     * 创建 Agent 实例（每个会话独立）
     * AiServices 自动生成代理：处理 LLM + Tool 循环 + ChatMemory
     *
     * @param sessionId 会话 ID（用于管理 ChatMemory）
     */
    public Assistant createAssistant(String sessionId) {

        //    Skills skills = Skills.from(ClassPathSkillLoader.loadSkill("skills/full"));
        FileSystemSkill skill = ClassPathSkillLoader.loadSkill("skills/full");
        //FileSystemSkill skill = FileSystemSkillLoader.loadSkill(Path.of("skills/full"));
        Skill skill_full = Skill.builder()
                .name(skill.name())
                .description(skill.description())
                .content(skill.content())
                .tools(projectTools, fileTools, recognitionTools, reportTools)
                .build();
        Skills skills = Skills.from(skill_full);

        return AiServices.builder(Assistant.class)
                .streamingChatModel(chatStreamModel)
                .chatMemory(memoryManager.getMemory(sessionId))
                .tools(
                        projectTools,
                        fileTools,
                        recognitionTools,
                        reportTools
                )
                .toolProvider(skills.toolProvider())
                .systemMessage(AgentSystemPrompt.SYSTEM_PROMPT)
                // or .toolProviders(myToolProvider, skills.toolProvider()) if you already have a tool provider configured
                .systemMessageTransformer(systemMessage -> systemMessage + "您可以使用以下技能:\n" + skills.formatAvailableSkills()
                        + "\n当用户的请求与其中一项技能相关时，在继续之前，首先使用“activate_skill”工具激活它。")
//                .systemMessageTransformer(systemMessage ->
//                        AgentSystemPrompt.SYSTEM_PROMPT + skill.content())
                .build();
    }
}
