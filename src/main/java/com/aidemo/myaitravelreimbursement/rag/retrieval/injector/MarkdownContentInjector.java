package com.aidemo.myaitravelreimbursement.rag.retrieval.injector;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;

import java.util.List;

/**
 * Markdown 格式的 ContentInjector，实现 LangChain4j {@link ContentInjector} 接口。
 * <p>
 * 将检索到的 Content 格式化为 Markdown 引用块，注入到 UserMessage 中：
 * <pre>
 * {{userMessage}}
 *
 * 请结合以下参考资料回答（如无相关内容请忽略）：
 * [1] 来源文件.pdf（相关度: 0.85）
 * （引用文本内容）
 * ---
 * </pre>
 */
public class MarkdownContentInjector implements ContentInjector {

    private static final String REFERENCE_TEMPLATE = """
            请结合以下参考资料回答（若无相关内容请忽略）：
            %s
            """;

    private static final String CONTENT_TEMPLATE = """
            [%d] **%s**（相关度: %.2f）
            %s
            """;

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        if (contents == null || contents.isEmpty()) {
            return chatMessage;
        }

        // 目前只支持 UserMessage
        if (!(chatMessage instanceof UserMessage um)) {
            return chatMessage;
        }

        StringBuilder refBuilder = new StringBuilder();

        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            String source = "未知来源";
            double score = 0.0;

            try {
                Object sourceVal = content.metadata().get("source_file");
                if (sourceVal != null) source = sourceVal.toString();
            } catch (Exception ignored) {}

            try {
                Object scoreVal = content.metadata().get("score");
                if (scoreVal instanceof Number n) score = n.doubleValue();
            } catch (Exception ignored) {}

            String text = content.textSegment().text();

            refBuilder.append(String.format(CONTENT_TEMPLATE,
                    i + 1,
                    source,
                    score,
                    truncate(text, 800)
            ));
            refBuilder.append("---\n");
        }

        String originalText = um.singleText();
        String injectedText = originalText + "\n\n" +
                String.format(REFERENCE_TEMPLATE, refBuilder);

        return UserMessage.from(injectedText);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
