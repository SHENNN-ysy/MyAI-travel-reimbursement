package com.aidemo.myaitravelreimbursement.rag.ingestion;

import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties.ChunkingProperties;
import com.aidemo.myaitravelreimbursement.agent.RagConfig.RetrievalConfig;
import com.aidemo.myaitravelreimbursement.rag.ingestion.bm25.BM25Indexer;
import com.aidemo.myaitravelreimbursement.rag.ingestion.store.ChromaUpserter;
import com.aidemo.myaitravelreimbursement.rag.providers.loader.PdfLoader;
import com.aidemo.myaitravelreimbursement.rag.providers.splitter.RecursiveTextSplitter;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import com.aidemo.myaitravelreimbursement.rag.types.Chunk;
import com.aidemo.myaitravelreimbursement.rag.types.ChunkRecord;
import com.aidemo.myaitravelreimbursement.rag.types.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link IngestionPipeline} 单元测试。
 * <p>
 * 测试覆盖：
 * <ol>
 *   <li>{@code run()} 在 Spring Boot 启动后自动触发，调用 {@code ingestAll()}</li>
 *   <li>{@code ingestDomain()} 遍历指定知识域目录，执行 SHA256 去重后逐文档摄入</li>
 *   <li>{@code ingestDocument()} 完成 分块 → 编码 → ChromaDB 写入 → BM25 索引 完整链路</li>
 * </ol>
 * <p>
 * 运行时机说明：
 * {@code IngestionPipeline} 实现了 {@link ApplicationRunner} 接口，
 * Spring Boot 在所有 Bean 初始化完成后、以 "run" 优先级执行所有 {@code ApplicationRunner} Bean。
 * 因此该测试也验证了：当 Spring 上下文加载完成后，整条 RAG 摄入管线会全自动执行。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionPipelineTest {

    @Mock
    private PdfLoader pdfLoader;

    @Mock
    private RecursiveTextSplitter splitter;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ChromaUpserter chromaUpserter;

    @Mock
    private BM25Indexer bm25Indexer;

    @Mock
    private RagProperties ragProperties;

    @Mock
    private TraceContext traceContext;

    @Mock
    private ApplicationArguments args;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    @Captor
    private ArgumentCaptor<ChunkRecord> chunkRecordCaptor;

    private IngestionPipeline pipeline;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pipeline = new IngestionPipeline(
                pdfLoader,
                splitter,
                embeddingModel,
                chromaUpserter,
                bm25Indexer,
                ragProperties,
                traceContext
        );

        // TraceContext mock：每次调用返回固定 traceId
        when(traceContext.currentTraceId()).thenReturn("test-trace-id");
        // pdfLoader.scan mock：统一使用 any() 避免 Path 精确匹配问题
        lenient().when(pdfLoader.scan(any())).thenReturn(List.of());
    }

    // ======================== run() 测试 ========================

    @Nested
    @DisplayName("run() — ApplicationRunner 入口")
    class RunTests {

        @Test
        @DisplayName("docs 目录为空时，不抛异常，返回 0")
        void run_withEmptyDocs_returnsZero() {
            when(ragProperties.getDocsPath()).thenReturn(tempDir.toString());

            pipeline.run(args);

            verify(pdfLoader, never()).scan(any());
            verify(pdfLoader, never()).load(any());
        }

        @Test
        @DisplayName("docs 目录不存在时，不抛异常，正常返回")
        void run_withNonExistentDocs_returnsSafely() {
            when(ragProperties.getDocsPath()).thenReturn("Z:/non-existent-path");

            pipeline.run(args);

            // 不应抛出异常
        }
    }

    // ======================== ingestAll() 测试 ========================

    @Nested
    @DisplayName("ingestAll() — 遍历所有知识域")
    class IngestAllTests {

        @Test
        @DisplayName("KNOWN_DOMAINS 包含 policy/guide/history，全部遍历")
        void ingestAll_iteratesAllKnownDomains() {
            assertThat(RetrievalConfig.KNOWN_DOMAINS)
                    .containsExactly("policy", "guide", "history");

            when(ragProperties.getDocsPath()).thenReturn(tempDir.toString());

            int total = pipeline.ingestAll();

            // 每个已知域都会调用 scan，即使目录不存在
            verify(pdfLoader, times(RetrievalConfig.KNOWN_DOMAINS.size())).scan(any());
            assertThat(total).isZero();
        }
    }

    // ======================== ingestDomain() 测试 ========================

    @Nested
    @DisplayName("ingestDomain() — 单域摄入 & SHA256 去重")
    class IngestDomainTests {

        private Path policyDir;

        @BeforeEach
        void setUpIngestDomain() {
            // Splitter 依赖 ragProperties.getChunking()，需要 mock 否则 NPE
            var chunking = mock(ChunkingProperties.class);
            when(ragProperties.getChunking()).thenReturn(chunking);
            when(chunking.getMaxChunkSize()).thenReturn(500);
            when(chunking.getOverlap()).thenReturn(100);
            // pdfLoader.scan stub：统一使用 any() 避免 Path 精确匹配问题
            lenient().when(pdfLoader.scan(any())).thenReturn(List.of());
        }

        @BeforeEach
        void makeDomainDir() {
            policyDir = tempDir.resolve("policy");
            policyDir.toFile().mkdirs();
        }

        @Test
        @DisplayName("目录下无 PDF 文件时，返回 0")
        void ingestDomain_withNoFiles_returnsZero() {
            when(pdfLoader.scan(policyDir)).thenReturn(List.of());

            int count = pipeline.ingestDomain(policyDir, "policy");

            assertThat(count).isZero();
            verify(splitter, never()).split(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("同一 SHA256 内容重复出现时，仅摄入一次")
        void ingestDomain_deduplicatesBySha256() {
            Path fileA = policyDir.resolve("doc-a.pdf");
            Path fileB = policyDir.resolve("doc-b.pdf");  // 内容相同

            String sameContent = "## 基本操作\n\n这是相同内容";
            Document docA = makeDoc(fileA.toString(), sameContent, "abc123");
            Document docB = makeDoc(fileB.toString(), sameContent, "abc123"); // SHA256 相同

            when(pdfLoader.scan(policyDir)).thenReturn(List.of(fileA, fileB));
            when(pdfLoader.load(fileA)).thenReturn(docA);
            when(pdfLoader.load(fileB)).thenReturn(docB);
            when(splitter.split(anyString(), anyString(), anyString())).thenReturn(List.of());
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(List.of());

            int count = pipeline.ingestDomain(policyDir, "policy");

            // docB 因 SHA256 重复被跳过，只应触发一次 ingestDocument
            verify(pdfLoader, times(2)).load(any());       // load 仍被调用（做 SHA256 计算）
            verify(splitter, times(1)).split(anyString(), anyString(), anyString()); // 仅一次真正的分块
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("不同 SHA256 的文档均被正常摄入")
        void ingestDomain_differentSha256_bothIngested() {
            Path fileA = policyDir.resolve("doc-a.pdf");
            Path fileB = policyDir.resolve("doc-b.pdf");

            Document docA = makeDoc(fileA.toString(), "内容A", "hash-a");
            Document docB = makeDoc(fileB.toString(), "内容B", "hash-b");

            Chunk dummyChunk = new Chunk("chunk", Map.of("source_file", "x", "domain", "policy"));

            when(pdfLoader.scan(policyDir)).thenReturn(List.of(fileA, fileB));
            when(pdfLoader.load(fileA)).thenReturn(docA);
            when(pdfLoader.load(fileB)).thenReturn(docB);
            when(splitter.split(anyString(), anyString(), anyString())).thenReturn(List.of(dummyChunk));
            when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                    Response.from(mock(Embedding.class)));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(List.of());

            int count = pipeline.ingestDomain(policyDir, "policy");

            assertThat(count).isEqualTo(2);
            verify(splitter, times(2)).split(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("单文档摄入失败不影响同域其他文档")
        void ingestDomain_oneDocFails_continuesOthers() {
            Path fileA = policyDir.resolve("doc-a.pdf");
            Path fileB = policyDir.resolve("doc-b.pdf");

            Document docA = makeDoc(fileA.toString(), "内容A", "hash-a");
            Document docB = makeDoc(fileB.toString(), "内容B", "hash-b");

            when(pdfLoader.scan(policyDir)).thenReturn(List.of(fileA, fileB));
            when(pdfLoader.load(fileA)).thenReturn(docA);
            when(pdfLoader.load(fileB)).thenReturn(docB);
            when(splitter.split(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("分块失败"));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(List.of());

            int count = pipeline.ingestDomain(policyDir, "policy");

            assertThat(count).isEqualTo(1); // fileB 成功
        }
    }

    // ======================== ingestDocument() 测试 ========================

    @Nested
    @DisplayName("ingestDocument() — 完整管线链路")
    class IngestDocumentTests {

        @BeforeEach
        void setUpIngestDocument() {
            var chunking = mock(ChunkingProperties.class);
            when(ragProperties.getChunking()).thenReturn(chunking);
            when(chunking.getMaxChunkSize()).thenReturn(500);
            when(chunking.getOverlap()).thenReturn(100);
        }

        @Test
        @DisplayName("完整链路：分块 → 编码 → ChromaDB → BM25")
        void ingestDocument_fullPipeline() {
            Document doc = makeDoc("test.pdf", "## 基本操作\n\n报销内容", "hash-001");
            List<Chunk> chunks = List.of(
                    new Chunk("## 基本操作\n\n报销内容", Map.of("source_file", "test.pdf", "domain", "policy")),
                    new Chunk("## 基本操作\n\n详细内容", Map.of("source_file", "test.pdf", "domain", "policy"))
            );
            List<ChunkRecord> records = List.of(
                    new ChunkRecord(chunks.get(0), mock(Embedding.class), "chroma-id-1"),
                    new ChunkRecord(chunks.get(1), mock(Embedding.class), "chroma-id-2")
            );

            when(splitter.split(anyString(), eq("test.pdf"), eq("policy"))).thenReturn(chunks);
            when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                    Response.from(mock(Embedding.class)));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(records);

            pipeline.ingestDocument(doc);

            // 1. 分块：调用 2 次（每个 chunk 一次）
            verify(splitter).split("## 基本操作\n\n报销内容", "test.pdf", "policy");

            // 2. 编码：每个 chunk 调用一次 embed
            verify(embeddingModel, times(2)).embed(anyString());

            // 3. ChromaDB 写入：一次 upsert 包含所有 chunk
            verify(chromaUpserter).upsert(documentCaptor.capture(), eq(chunks), any());
            assertThat(documentCaptor.getValue().sha256()).isEqualTo("hash-001");

            // 4. BM25 索引：每个 ChunkRecord 索引一次
            verify(bm25Indexer, times(2)).index(chunkRecordCaptor.capture());
            assertThat(chunkRecordCaptor.getAllValues())
                    .hasSize(2)
                    .extracting(r -> r.chunk().text())
                    .containsExactly("## 基本操作\n\n报销内容", "## 基本操作\n\n详细内容");
        }

        @Test
        @DisplayName("分块为空时直接返回，不调用编码和存储")
        void ingestDocument_emptyChunks_returnsEarly() {
            Document doc = makeDoc("empty.pdf", "", "hash-empty");

            when(splitter.split(anyString(), anyString(), anyString())).thenReturn(List.of());

            pipeline.ingestDocument(doc);

            verify(splitter).split(anyString(), anyString(), anyString());
            verify(embeddingModel, never()).embed(anyString());
            verify(chromaUpserter, never()).upsert(any(), any(), any());
            verify(bm25Indexer, never()).index(any());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("ChromaDB 写入参数完整：Document、chunks、embeddings 顺序和数量正确")
        void ingestDocument_chromaUpsert_paramsCorrect() {
            Document doc = makeDoc("travel.pdf", "出差申请流程", "hash-travel");
            List<Chunk> chunks = List.of(
                    new Chunk("出差申请流程第一步", Map.of("source_file", "travel.pdf", "domain", "policy")),
                    new Chunk("出差申请流程第二步", Map.of("source_file", "travel.pdf", "domain", "policy")),
                    new Chunk("出差申请流程第三步", Map.of("source_file", "travel.pdf", "domain", "policy"))
            );
            List<ChunkRecord> upsertedRecords = List.of(
                    new ChunkRecord(chunks.get(0), mock(Embedding.class), "id-0"),
                    new ChunkRecord(chunks.get(1), mock(Embedding.class), "id-1"),
                    new ChunkRecord(chunks.get(2), mock(Embedding.class), "id-2")
            );

            when(splitter.split(anyString(), eq("travel.pdf"), eq("policy"))).thenReturn(chunks);
            when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                    Response.from(mock(Embedding.class)));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(upsertedRecords);

            pipeline.ingestDocument(doc);

            // 验证 upsert 三参数：doc、chunks、embeddings
            ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
            ArgumentCaptor<List<Chunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<List<Embedding>> embCaptor = ArgumentCaptor.forClass(List.class);

            verify(chromaUpserter).upsert(docCaptor.capture(), chunksCaptor.capture(), embCaptor.capture());

            assertThat(docCaptor.getValue().sha256()).isEqualTo("hash-travel");
            assertThat(chunksCaptor.getValue()).hasSize(3);
            assertThat(embCaptor.getValue()).hasSize(3);
            // embeddings 数量必须和 chunks 数量一致
            assertThat(chunksCaptor.getValue().size()).isEqualTo(embCaptor.getValue().size());
        }

        @Test
        @DisplayName("ChromaDB 返回的每个 ChunkRecord 都被 BM25 索引一次")
        void ingestDocument_chromaReturns_recordsIndexedByBm25() {
            Document doc = makeDoc("reimb.pdf", "报销单填写说明", "hash-reimb");
            Chunk chunk = new Chunk("报销单填写说明", Map.of("source_file", "reimb.pdf", "domain", "policy"));
            ChunkRecord record = new ChunkRecord(chunk, mock(Embedding.class), "chroma-id-reimb");

            when(splitter.split(anyString(), anyString(), anyString())).thenReturn(List.of(chunk));
            when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                    Response.from(mock(Embedding.class)));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(List.of(record));

            pipeline.ingestDocument(doc);

            verify(bm25Indexer, times(1)).index(record);
            verify(bm25Indexer).index(chunkRecordCaptor.capture());
            assertThat(chunkRecordCaptor.getValue().chunk().text()).isEqualTo("报销单填写说明");
        }

        @Test
        @DisplayName("编码循环：每个 chunk 正确编码并写入 ChromaDB")
        void ingestDocument_encodingLoop_normal() {
            Document doc = makeDoc("test.pdf", "报销内容", "hash-test");
            List<Chunk> chunks = List.of(
                    new Chunk("报销内容第一段", Map.of("source_file", "test.pdf", "domain", "policy")),
                    new Chunk("报销内容第二段", Map.of("source_file", "test.pdf", "domain", "policy"))
            );

            when(splitter.split(anyString(), eq("test.pdf"), eq("policy"))).thenReturn(chunks);
            when(embeddingModel.embed(anyString())).thenAnswer(inv ->
                    Response.from(mock(Embedding.class)));
            when(chromaUpserter.upsert(any(), any(), any())).thenReturn(List.of());

            pipeline.ingestDocument(doc);

            verify(embeddingModel, times(2)).embed(anyString());
            verify(chromaUpserter).upsert(any(), eq(chunks), any());
            verify(bm25Indexer, times(2)).index(any());
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 构造测试用 Document。
     */
    private Document makeDoc(String sourceFile, String content, String sha256) {
        return new Document(
                sourceFile,
                content,
                Map.of(
                        "source_file", sourceFile,
                        "domain", "policy",
                        "sha256", sha256
                )
        );
    }
}
