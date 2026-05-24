package com.aidemo.myaitravelreimbursement.rag.providers.loader;

import com.aidemo.myaitravelreimbursement.rag.types.Document;

import java.nio.file.Path;
import java.util.List;

/**
 * 文档加载器抽象接口。
 */
public interface DocumentLoader {

    /**
     * 从指定路径加载文档。
     *
     * @param path 文件路径
     * @return 解析后的 Document
     */
    Document load(Path path);

    /**
     * 扫描目录下所有支持的文件。
     *
     * @param dir 目录路径
     * @return 文件路径列表
     */
    List<Path> scan(Path dir);
}
