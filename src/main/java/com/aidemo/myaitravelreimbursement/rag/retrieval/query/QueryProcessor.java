package com.aidemo.myaitravelreimbursement.rag.retrieval.query;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询预处理：中文分词 + 关键词提取。
 * <p>
 * 基于 Lucene StandardAnalyzer 进行分词，
 * 提取关键词列表供 BM25 检索使用。
 */
@Component
public class QueryProcessor {

    /**
     * 对查询文本进行分词，返回关键词列表。
     *
     * @param query 原始查询
     * @return 分词后的关键词列表
     */
    public List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        try (Analyzer analyzer = new StandardAnalyzer()) {
            var tokenStream = analyzer.tokenStream("field", new StringReader(query));
            var termAttr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                String term = termAttr.toString();
                if (term.length() > 1) { // 过滤单字符
                    tokens.add(term);
                }
            }
            tokenStream.end();
        } catch (Exception e) {
            // Fallback: split by whitespace
            for (String part : query.split("\\s+")) {
                if (!part.trim().isEmpty()) tokens.add(part.trim());
            }
        }
        return tokens;
    }

    /**
     * 构建 Lucene 查询语法字符串（多关键词 OR 连接）。
     *
     * @param query 原始查询
     * @return Lucene query string，如 "关键词1 OR 关键词2"
     */
    public String toLuceneQuery(String query) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return query;
        return String.join(" OR ", tokens);
    }
}
