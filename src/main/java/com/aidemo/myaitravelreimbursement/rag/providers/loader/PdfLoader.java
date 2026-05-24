package com.aidemo.myaitravelreimbursement.rag.providers.loader;

import com.aidemo.myaitravelreimbursement.rag.types.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * PDF 文档加载器，基于 Apache PDFBox。
 * <p>
 * 支持文件类型：.pdf、.txt（fallback）
 */
@Slf4j
@Component
public class PdfLoader implements DocumentLoader {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".txt", ".md");

    @Override
    public Document load(Path path) {
        String ext = getExtension(path).toLowerCase();
        String content;
        try {
            content = switch (ext) {
                case ".pdf" -> extractPdfText(path);
                case ".txt", ".md" -> Files.readString(path);
                default -> throw new IllegalArgumentException("Unsupported file type: " + ext);
            };
        } catch (IOException e) {
            log.error("Failed to load document: {}", path, e);
            content = "";
        }

        String sha256 = sha256(content);
        String domain = inferDomain(path);

        Map<String, String> metadata = Map.of(
                "source_file", path.getFileName().toString(),
                "domain", domain,
                "sha256", sha256,
                "file_size", String.valueOf(content.length())
        );

        return new Document(path.toAbsolutePath().toString(), content, metadata);
    }

    @Override
    public List<Path> scan(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> SUPPORTED_EXTENSIONS.contains(getExtension(p).toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", dir, e);
            return List.of();
        }
    }

    private String extractPdfText(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path);
             PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** 根据路径推断知识域 */
    private String inferDomain(Path path) {
        String pathStr = path.toString().toLowerCase();
        if (pathStr.contains("/policy/") || pathStr.contains("\\policy\\")) return "policy";
        if (pathStr.contains("/guide/") || pathStr.contains("\\guide\\")) return "guide";
        if (pathStr.contains("/history/") || pathStr.contains("\\history\\")) return "history";
        return "policy"; // 默认域
    }

    private static String getExtension(Path p) {
        String name = p.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx) : "";
    }
}
