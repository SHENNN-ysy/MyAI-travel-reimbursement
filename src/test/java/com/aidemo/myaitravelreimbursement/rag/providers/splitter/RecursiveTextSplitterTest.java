package com.aidemo.myaitravelreimbursement.rag.providers.splitter;

import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import com.aidemo.myaitravelreimbursement.rag.ingestion.store.ChromaUpserter;
import com.aidemo.myaitravelreimbursement.rag.types.Chunk;
import com.aidemo.myaitravelreimbursement.rag.types.ChunkRecord;
import com.aidemo.myaitravelreimbursement.rag.types.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link RecursiveTextSplitter} 分块效果观察测试。
 * <p>
 * 测试文件放在 {@code src/test/resources/rag/splitter/} 目录下，
 * 支持 .txt、.md、.pdf 文件。
 * <p>
 * 运行后会打印每个 chunk 的详细信息到控制台，便于人工观察分块效果。
 */
class RecursiveTextSplitterTest {

    private RecursiveTextSplitter splitter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var chunking = new RagProperties.ChunkingProperties();
        chunking.setMaxChunkSize(1000);
        chunking.setOverlap(100);

        RagProperties ragProperties = Mockito.mock(RagProperties.class);
        when(ragProperties.getChunking()).thenReturn(chunking);

        splitter = new RecursiveTextSplitter(ragProperties);
    }

    @Test
    @DisplayName("从 test/resources 加载真实文件并观察分块结果")
    void split_realFile_visualizeChunking() throws IOException {
        String fileName = "AI报销助手使用指南.md"; // <-- 替换为你的文件名
        String content = Files.readString(
                Path.of("src/test/resources/rag/splitter").resolve(fileName)
        );

        List<Chunk> chunks = splitter.split(content, fileName, "policy");

        // 打印分块结果
        printChunks(chunks);

        // 基础断言
        assertThat(chunks).isNotEmpty();
    }

    // ======================== 辅助方法 ========================

    private void printChunks(List<Chunk> chunks) {
        System.out.println("=".repeat(70));
        System.out.printf("  文档分块结果：共 %d 个 Chunk%n", chunks.size());
        System.out.println("=".repeat(70));

        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            int lines = c.text().split("\n").length;

            System.out.printf("%n--- Chunk #%d [%d 行, %d 字符] ---%n",
                    i, lines, c.text().length());
            System.out.println("H1: " + nullSafe(c.metadata().get("h1_heading")));
            System.out.println("H2: " + nullSafe(c.metadata().get("h2_heading")));
            System.out.println("source_file: " + c.sourceFile());
            System.out.println("-".repeat(50));
            System.out.println(c.text());
        }
        System.out.println("=".repeat(70));
    }

    private static String nullSafe(String s) {
        return s != null ? s : "(无)";
    }


    @Test
    @DisplayName("分块 → 编码 → ChromaDB 写入，输出每个 ChunkRecord")
    void ingestDocument() throws IOException {
        String fileName = "AI报销助手使用指南.md";
        String content = Files.readString(
                Path.of("src/test/resources/rag/splitter").resolve(fileName)
        );
        Document doc = new Document(fileName, content,
                Map.of("source_file", fileName, "domain", "policy", "sha256", "test-sha"));

        List<Chunk> chunks = splitter.split(content, fileName, "policy");
        assertThat(chunks).isNotEmpty();

        // Mock EmbeddingModel：返回伪造向量，避免真实网络调用
        EmbeddingModel mockEmbeddingModel = Mockito.mock(EmbeddingModel.class);
        var embeddings = new java.util.ArrayList<Embedding>();
        for (int i = 0; i < chunks.size(); i++) {
            embeddings.add(dev.langchain4j.data.embedding.Embedding.from(
                    new float[512] // BgeSmallZhV15 向量维度 512
            ));
        }
        when(mockEmbeddingModel.embed(content)).thenReturn(
                new dev.langchain4j.model.output.Response<>(embeddings.get(0)));

        // Mock ChromaUpserter：捕获 upsert 调用参数，打印 ChunkRecord
        ChromaUpserter mockChromaUpserter = Mockito.mock(ChromaUpserter.class);
        when(mockChromaUpserter.upsert(doc, chunks, embeddings)).thenReturn(
                java.util.stream.IntStream.range(0, chunks.size())
                        .mapToObj(i -> new ChunkRecord(chunks.get(i), embeddings.get(i), "chunk-" + i))
                        .toList()
        );

        // 执行 upsert
        List<ChunkRecord> records = mockChromaUpserter.upsert(doc, chunks, embeddings);

        // 打印每个 ChunkRecord
        printChunkRecords(records);
    }

    private void printChunkRecords(List<ChunkRecord> records) {
        System.out.println("=".repeat(70));
        System.out.printf("  ChromaDB 写入结果：共 %d 条 ChunkRecord%n", records.size());
        System.out.println("=".repeat(70));

        for (int i = 0; i < records.size(); i++) {
            ChunkRecord r = records.get(i);
            Chunk c = r.chunk();
            System.out.printf("%n--- ChunkRecord #%d ---%n", i);
            System.out.printf("  chromaId : %s%n", r.chromaId());
            System.out.printf("  vectorDim: %d%n", r.embedding().vectorAsList().size());
            System.out.printf("  source   : %s%n", c.sourceFile());
            System.out.printf("  h1_heading: %s%n", nullSafe(c.metadata().get("h1_heading")));
            System.out.printf("  h2_heading: %s%n", nullSafe(c.metadata().get("h2_heading")));
            System.out.printf("  chunk_index: %s%n", nullSafe(c.metadata().get("chunk_index")));
            System.out.printf("  charCount : %d%n", c.text().length());
            System.out.println("-".repeat(50));
            System.out.println(c.text());
        }
        System.out.println("=".repeat(70));
    }
}

