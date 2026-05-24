package com.aidemo.myaitravelreimbursement.rag.retrieval.router;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的知识域查询路由器，实现 LangChain4j {@link QueryRouter} 接口。
 * <p>
 * 使用 ChatModel 让 LLM 判断用户查询应路由到哪些知识域，
 * 然后返回对应域的 ContentRetriever。
 */
@Slf4j
public class KnowledgeDomainRouter implements QueryRouter {

    private final ChatModel chatModel;
    private final Map<String, ContentRetriever> retrievers;
    private final List<String> knownDomains;

    private static final String ROUTING_PROMPT_TEMPLATE = """
            你是一个查询路由器。请判断用户查询最可能涉及哪个知识域。

            可用知识域：
            - policy（报销政策、制度文件）
            - guide（操作指南、使用手册）
            - history（历史案例、常见问题）

            用户查询：%s

            请只输出知识域名称（policy/guide/history），如有多个用逗号分隔，不要加任何解释。
            """;

    public KnowledgeDomainRouter(
            ChatModel chatModel,
            Map<String, ContentRetriever> retrievers,
            List<String> knownDomains) {
        this.chatModel = chatModel;
        this.retrievers = retrievers;
        this.knownDomains = knownDomains;
    }

    @Override
    public List<ContentRetriever> route(Query query) {
        try {
            String prompt = String.format(ROUTING_PROMPT_TEMPLATE, query.text());
            String responseText = chatModel.chat(prompt);

            List<String> domains = parseDomains(responseText);
            log.debug("QueryRouter: query='{}', resolved domains={}", query.text(), domains);

            return domains.stream()
                    .filter(retrievers::containsKey)
                    .map(retrievers::get)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("KnowledgeDomainRouter failed, using all retrievers as fallback: {}", e.getMessage());
            return retrievers.values().stream().distinct().collect(Collectors.toList());
        }
    }

    private List<String> parseDomains(String llmOutput) {
        List<String> domains = new ArrayList<>();
        for (String token : llmOutput.split("[,，、\n]")) {
            String d = token.trim().toLowerCase();
            if (knownDomains.contains(d)) {
                domains.add(d);
            }
        }
        return domains;
    }
}
