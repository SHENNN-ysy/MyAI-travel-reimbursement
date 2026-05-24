package com.aidemo.myaitravelreimbursement.rag.retrieval.query;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 LLM 的查询扩展变换器。
 * <p>
 * 使用 ChatModel 将用户查询扩展为多个版本（增加同义词、口语化表达），
 * 提升召回率。
 */
public class ExpandingQueryTransformer implements QueryTransformer {

    private final ChatModel chatModel;

    private static final String EXPANSION_PROMPT = """
            你是一个查询扩展助手。请将以下用户查询扩展为3个不同的表述版本，
            保持语义相同但措辞不同，以便进行多路检索召回。
            只输出扩展后的查询，每行一个，不要加编号或说明。

            原始查询：%s

            扩展结果：
            """;

    public ExpandingQueryTransformer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public List<Query> transform(Query query) {
        String prompt = String.format(EXPANSION_PROMPT, query.text());

        try {
            ChatResponse response = chatModel.chat(UserMessage.from(prompt));
            String responseText = response.aiMessage().text();

            List<String> expanded = new ArrayList<>();
            for (String line : responseText.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    expanded.add(trimmed);
                }
            }

            ArrayList<Query> result = new ArrayList<>();
            result.add(query);
            for (String expandedText : expanded) {
                result.add(new Query(expandedText, query.metadata()));
            }
            return result;
        } catch (Exception e) {
            return List.of(query);
        }
    }
}
