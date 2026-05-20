package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.memory.AgentChatMemoryManager;
import com.aidemo.myaitravelreimbursement.agent.prompt.AgentSystemPrompt;
import com.aidemo.myaitravelreimbursement.agent.tools.FileTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ProjectTools;
import com.aidemo.myaitravelreimbursement.agent.tools.RecognitionTools;
import com.aidemo.myaitravelreimbursement.agent.tools.ReportTools;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
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
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

//    @Resource
//    private ContentRetriever userguide_contentRetriever;
//
//    @Resource
//    private ContentRetriever capability_contentRetriever;
//
//    @Resource
//    private ContentRetriever emptyRetriever;

    @Resource
    private ContentRetriever help_contentRetriever;

    // ========== MCP Excel 工具提供者 ==========
    private ToolProvider excelMcpToolProvider;
    private McpClient excelMcpClient;

    @Value("${excel-mcp.url:}")
    private String excelMcpUrl;

    @PostConstruct
    public void initMcpClient() {
        if (!StringUtils.hasText(excelMcpUrl)) {
            log.warn("excel-mcp.url 未配置，Excel MCP 工具将不启用");
            return;
        }

        try {
            McpTransport transport = StreamableHttpMcpTransport.builder()
                    .url(excelMcpUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            excelMcpClient = DefaultMcpClient.builder()
                    .key("excel-mcp")
                    .transport(transport)
                    .build();

            excelMcpToolProvider = McpToolProvider.builder()
                    .mcpClients(excelMcpClient)
                    .build();

            log.info("Excel MCP 客户端初始化成功，连接地址: {}", excelMcpUrl);
        } catch (Exception e) {
            log.warn("Excel MCP 客户端初始化失败，将不启用 Excel 工具: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void closeMcpClient() {
        if (excelMcpClient != null) {
            try {
                excelMcpClient.close();
                log.info("Excel MCP 客户端已关闭");
            } catch (Exception e) {
                log.warn("关闭 Excel MCP 客户端时出错: {}", e.getMessage());
            }
        }
    }

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

//        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);
        //QueryTransformer queryTransformer1 = new ExpandingQueryTransformer(chatModel());
//        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
//        retrieverToDescription.put(userguide_contentRetriever, "AI报销助手--产品介绍使用说明，包含报销流程、操作步骤、使用技巧、工具介绍、常见问题解答等文档");
//        retrieverToDescription.put(capability_contentRetriever, "AI报销助手--工具操作能力说明，包含报销流程、工具能力介绍、调用方式、输入输出说明等文档");
//        retrieverToDescription.put(emptyRetriever, "闲聊、问候、天气、不相关话题，或其他不属于上述技术/职业的问题");

//        QueryRouter queryRouter = new LanguageModelQueryRouter(chatModel, retrieverToDescription);
//
//        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
//                .queryTransformer(queryTransformer)
//                .queryRouter(queryRouter)
//                .build();

        FileSystemSkill skill = ClassPathSkillLoader.loadSkill("skills/full");
        Skill skill_full = Skill.builder()
                .name(skill.name())
                .description(skill.description())
                .content(skill.content())
                .toolProviders(skill.toolProviders())
                .tools(projectTools, fileTools, recognitionTools, reportTools)
                .build();
        Skills skills = Skills.from(skill_full);

        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .streamingChatModel(chatStreamModel)
                .chatMemory(memoryManager.getMemory(sessionId))
                //.retrievalAugmentor(retrievalAugmentor)
                .contentRetriever(help_contentRetriever)
                .tools(
                        projectTools,
                        fileTools,
                        recognitionTools,
                        reportTools
                )
                .toolProvider(skills.toolProvider())
                .toolProvider(excelMcpToolProvider)
                .systemMessage(AgentSystemPrompt.SYSTEM_PROMPT)
                .systemMessageTransformer(systemMessage -> systemMessage + "您可以使用以下技能:\n" + skills.formatAvailableSkills()
                        + "\n当用户的请求与其中一项技能相关时，在继续之前，首先使用\"activate_skill\"工具激活它。");

        // 如果 MCP 初始化成功，则添加 Excel 工具
        if (excelMcpToolProvider != null) {
            builder.toolProvider(excelMcpToolProvider);
        }

        return builder.build();
    }
}
