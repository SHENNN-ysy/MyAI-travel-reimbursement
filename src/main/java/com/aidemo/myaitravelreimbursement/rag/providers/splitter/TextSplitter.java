package com.aidemo.myaitravelreimbursement.rag.providers.splitter;

import com.aidemo.myaitravelreimbursement.rag.types.Chunk;

import java.util.List;

/**
 * 文本分块器抽象接口。
 */
public interface TextSplitter {

    /**
     * 将文档文本切分为多个 Chunk。
     *
     * @param text        完整文本
     * @param fileName   来源文件名（用于元数据）
     * @param domain     知识域
     * @return Chunk 列表
     */
    List<Chunk> split(String text, String fileName, String domain);
}
