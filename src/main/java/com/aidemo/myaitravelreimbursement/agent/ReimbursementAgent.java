package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.memory.AgentChatMemoryManager;
import com.aidemo.myaitravelreimbursement.agent.prompt.AgentSystemPrompt;
import com.aidemo.myaitravelreimbursement.agent.tools.FileTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ProjectTools;
import com.aidemo.myaitravelreimbursement.agent.tools.RecognitionTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ReportTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.skills.*;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 对话服务（基于 LangChain4j AiServices）
 * 定义 Agent 行为接口，由 AiServices 自动生成实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
public class ReimbursementAgent {

    private final ChatModel chatModel;
    private final StreamingChatModel chatStreamModel;
    private final AgentChatMemoryManager memoryManager;
    private final ProjectTools projectTools;
    private final FileTools fileTools;
    private final RecognitionTools recognitionTools;
    private final ReportTools reportTools;

    @Resource
    private ContentRetriever userguide_contentRetriever;

    @Resource
    private ContentRetriever capability_contentRetriever;

    @Resource
    private ContentRetriever emptyRetriever;
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

        //QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);
        //QueryTransformer queryTransformer1 = new ExpandingQueryTransformer(chatModel());
        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
//        retrieverToDescription.put(capability_contentRetriever, "AI报销助手--工具能力说明，包含自动化工具能力、一键全流程、使用限制；【重要：仅在用户需要了解AI报销助手时进行自我介绍时使用，不参与实际工作流】");
//        retrieverToDescription.put(userguide_contentRetriever, "AI报销助手--产品介绍使用说明，包含基本操作、使用技巧、报销全流程操作说明、常见问题、注意事项；【重要：仅在用户需要了解AI报销助手时进行自我介绍时使用，不参与实际工作流】");
//        retrieverToDescription.put(emptyRetriever, "闲聊、问候、天气、不相关话题，或其他不属于上述内容的问题");
        retrieverToDescription.put(userguide_contentRetriever,
                "AI报销助手的产品介绍使用说明，包含基本操作、使用技巧、报销全流程操作说明、常见问题、注意事项" +
                        "仅在用户明确询问「AI报销助手是什么」「怎么使用」「有哪些功能介绍」时才使用。"
                        + "如果用户是在请求完成实际报销操作、执行工作流、提交报销单等任务，不要选择此源。");
        retrieverToDescription.put(capability_contentRetriever,
                "AI报销助手的工具能力说明，包含自动化工具能力、一键全流程、使用限制。" +
                        "仅在用户询问工具的技术细节与说明、调用限制时才使用。"
                        + "如果用户请求完成实际报销操作、执行工作流等，不要选择此源。");
        retrieverToDescription.put(emptyRetriever,
                         "如果用户请求在需要执行明确的任务，如完成报销流程、执行报销，识别发票等操作，请选择此源（实际报销任务由工具执行，不从文档检索）");

        QueryRouter queryRouter = new LanguageModelQueryRouter(chatModel, retrieverToDescription);

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                //.queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .build();

        FileSystemSkill skill = ClassPathSkillLoader.loadSkill("skills/full");
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
                .retrievalAugmentor(retrievalAugmentor)
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
                .build();
    }
}
