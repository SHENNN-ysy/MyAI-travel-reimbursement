package com.aidemo.myaitravelreimbursement.rag.retrieval;

import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import com.aidemo.myaitravelreimbursement.agent.ReimbursementAgent;
import com.aidemo.myaitravelreimbursement.rag.retrieval.retriever.HybridSearchContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索链路集成测试。
 *
 * <p>测试整个检索流程：用户输入 → EmbeddingDomainRouter 路由 → HybridSearchContentRetriever
 * (BM25 + 向量检索 + RRF 融合) → HybridContentAggregator 合并 → MarkdownContentInjector 注入。
 *
 * <p>运行前确保已完成文档摄入（启动一次 Spring Boot 应用即可自动触发 IngestionPipeline）。
 */
@SpringBootTest
@DisplayName("RAG 检索链路集成测试")
class RetrievalPipelineTest {

    @MockBean
    private ReimbursementAgent reimbursementAgent;

    @Autowired
    @Qualifier("policyContentRetriever")
    private HybridSearchContentRetriever policyRetriever;

    @Autowired
    @Qualifier("guideContentRetriever")
    private HybridSearchContentRetriever guideRetriever;

    @Autowired
    @Qualifier("historyContentRetriever")
    private HybridSearchContentRetriever historyRetriever;

    @Autowired
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        System.out.println("=".repeat(80));
        System.out.println("RAG 检索链路集成测试");
        System.out.println("docs-path: " + ragProperties.getDocsPath());
        System.out.println("max-results: " + ragProperties.getSearch().getMaxResults());
        System.out.println("dense-weight: " + ragProperties.getSearch().getDenseWeight());
        System.out.println("sparse-weight: " + ragProperties.getSearch().getSparseWeight());
        System.out.println("min-score: " + ragProperties.getSearch().getMinScore());
        System.out.println("=".repeat(80));
    }

    /**
     * 执行一次检索并打印结果。
     */
    private List<Content> doRetrieve(String query, HybridSearchContentRetriever retriever) {
        Query ragQuery = Query.from(query);
        List<Content> contents = retriever.retrieve(ragQuery);

        System.out.println();
        System.out.println("-".repeat(60));
        System.out.println("查询: " + query);
        System.out.println("命中结果数: " + contents.size());

        for (int i = 0; i < contents.size(); i++) {
            Content c = contents.get(i);
            String source = "未知来源";
            double score = 0.0;
            try {
                Object s = c.metadata().get("source_file");
                if (s != null) source = s.toString();
            } catch (Exception ignored) {}
            try {
                Object sc = c.metadata().get("score");
                if (sc instanceof Number n) score = n.doubleValue();
            } catch (Exception ignored) {}

            String text = c.textSegment().text();
            System.out.println("  [" + (i + 1) + "] 来源: " + source + "  (得分: " + score + ")");
            System.out.println("      内容: " + (text.length() > 300 ? text.substring(0, 300) + "..." : text));
        }
        return contents;
    }

    // -------------------------------------------------------------------------
    // 各知识域检索测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("检索测试 - policy 域：发票丢失")
    void testPolicyRetrieval_fapiao() {
        List<Content> contents = doRetrieve("发票丢失了怎么办", policyRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - policy 域：住宿费标准")
    void testPolicyRetrieval_accommodation() {
        List<Content> contents = doRetrieve("住宿费能报销多少", policyRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - policy 域：机票报销规定")
    void testPolicyRetrieval_flight() {
        List<Content> contents = doRetrieve("机票报销有什么规定", policyRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - policy 域：餐饮补贴标准")
    void testPolicyRetrieval_meals() {
        List<Content> contents = doRetrieve("出差餐饮补贴是多少", policyRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - guide 域：如何使用助手")
    void testGuideRetrieval_usage() {
        List<Content> contents = doRetrieve("怎么使用报销助手", guideRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - guide 域：一键报销操作")
    void testGuideRetrieval_full() {
        List<Content> contents = doRetrieve("一键报销怎么操作", guideRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - history 域：票据丢失案例")
    void testHistoryRetrieval_lost() {
        List<Content> contents = doRetrieve("票据丢失怎么处理", historyRetriever);
        assertThat(contents).isNotEmpty();
    }

    @Test
    @DisplayName("检索测试 - history 域：超标报销案例")
    void testHistoryRetrieval_over() {
        List<Content> contents = doRetrieve("超标报销会怎样", historyRetriever);
        assertThat(contents).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // 手动模拟完整链路：Router → Retriever → Injector
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("手动模拟完整 RAG 链路并打印最终 Prompt")
    void testFullRagChain() {
        String userQuery = "发票丢失了怎么办";

        System.out.println("\n========== 完整链路测试 ==========");
        System.out.println("用户输入: " + userQuery);

        // ① EmbeddingDomainRouter 路由 → policy 域（这里直接指定，实际由 router 决定）
        HybridSearchContentRetriever retriever = policyRetriever;
        System.out.println("路由结果: policy 域");

        // ② HybridSearchContentRetriever 检索（内部完成 BM25 + 向量 + RRF 融合）
        List<Content> contents = doRetrieve(userQuery, retriever);

        // ③ 模拟 MarkdownContentInjector：拼装最终发给 LLM 的 prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append(userQuery).append("\n\n");
        prompt.append("请结合以下参考资料回答（若无相关内容请忽略）：\n");

        for (int i = 0; i < contents.size(); i++) {
            Content c = contents.get(i);
            String text = c.textSegment().text();
            if (text.length() > 400) text = text.substring(0, 400) + "...";

            String src = "未知来源";
            try {
                Object s = c.metadata().get("source_file");
                if (s != null) src = s.toString();
            } catch (Exception ignored) {}
            double score = 0.0;
            try {
                Object sc = c.metadata().get("score");
                if (sc instanceof Number n) score = n.doubleValue();
            } catch (Exception ignored) {}

            prompt.append("[").append(i + 1).append("] **").append(src)
                    .append("**（相关度: ").append(String.format("%.2f", score)).append("）\n")
                    .append(text).append("\n---\n");
        }

        System.out.println();
        System.out.println("-".repeat(60));
        System.out.println("最终注入 LLM 的 Prompt（模拟 MarkdownContentInjector）:");
        System.out.println("-".repeat(60));
        System.out.println(prompt);
        System.out.println("========== 链路测试结束 ==========");

        assertThat(contents).isNotEmpty();
        assertThat(prompt.toString()).contains(userQuery);
    }
}
