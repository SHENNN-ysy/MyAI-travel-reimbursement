package com.aidemo.myaitravelreimbursement.agent.RagConfig;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> capability_embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public EmbeddingStore<TextSegment> userguide_embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public ContentRetriever capability_contentRetriever() {
        // ------ RAG ------
        // 1. 加载文档
        List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs/capability");
        // 2. 按 ## 二级标题切分：每个 chunk 从一个 ## 标题开始
        DocumentByRegexSplitter splitter = new DocumentByRegexSplitter(
                "(?m)^##\\s",  // regex: 匹配 ## 开头的行
                "",             // joinDelimiter: 段内合并时用空字符串连接
                5000,           // maxSegmentSizeInChars: 每个 chunk 最大字符数
                0               // maxOverlapSizeInChars: 不重叠
        );
        // 3. 自定义文档加载器
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                // 为了提高搜索质量，为每个 TextSegment 添加文档名称
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                // 使用指定的向量模型
                .embeddingModel(embeddingModel())
                .embeddingStore(capability_embeddingStore())
                .build();
        // 加载文档
        ingestor.ingest(documents);
        // 4. 自定义内容查询器
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(capability_embeddingStore())
                .embeddingModel(embeddingModel())
                .maxResults(3) // 最多 5 个检索结果
                .minScore(0.75) // 过滤掉分数小于 0.75 的结果
                .build();


        return contentRetriever;
    }
    @Bean
    public ContentRetriever userguide_contentRetriever() {
        // ------ RAG ------
        // 1. 加载文档
        List<Document> documents = FileSystemDocumentLoader.loadDocuments("src/main/resources/docs/userguide");
        // 2. 按 ## 二级标题切分：每个 chunk 从一个 ## 标题开始
        DocumentByRegexSplitter splitter = new DocumentByRegexSplitter(
                "(?m)^##\\s",  // regex: 匹配 ## 开头的行
                "",             // joinDelimiter: 段内合并时用空字符串连接
                5000,           // maxSegmentSizeInChars: 每个 chunk 最大字符数
                0               // maxOverlapSizeInChars: 不重叠
        );
        // 3. 自定义文档加载器
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                // 为了提高搜索质量，为每个 TextSegment 添加文档名称
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                // 使用指定的向量模型
                .embeddingModel(embeddingModel())
                .embeddingStore(userguide_embeddingStore())
                .build();
        // 加载文档
        ingestor.ingest(documents);
        // 4. 自定义内容查询器
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(userguide_embeddingStore())
                .embeddingModel(embeddingModel())
                .maxResults(3) // 最多 5 个检索结果
                .minScore(0.75) // 过滤掉分数小于 0.75 的结果
                .build();


        return contentRetriever;
    }

    @Bean
    public ContentRetriever emptyRetriever() {

        // 空检索器：不检索任何内容，直接返回空列表
        return new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                return Collections.emptyList();
            }
        };
    }
}

