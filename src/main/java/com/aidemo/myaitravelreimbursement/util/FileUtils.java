package com.aidemo.myaitravelreimbursement.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件工具类
 */
public class FileUtils {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "pdf", "jpg", "jpeg", "png", "heic", "gif", "webp"
    );

    /**
     * 生成安全的存储文件名
     */
    public static String generateStorageName(String originalName) {
        String extension = getExtension(originalName);
        return UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    /**
     * 获取文件扩展名
     */
    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 获取文件的 MIME 类型
     */
    public static String getMimeType(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "heic" -> "image/heic";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 检查文件扩展名是否允许
     */
    public static boolean isExtensionAllowed(String filename, List<String> allowedExtensions) {
        String ext = getExtension(filename).toLowerCase();
        return allowedExtensions.contains(ext);
    }

    /**
     * 安全路径校验：防止路径穿越攻击
     */
    public static boolean isPathSafe(String basePath, String fullPath) {
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path target = Paths.get(fullPath).toAbsolutePath().normalize();
            return target.startsWith(base);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建目录（如果不存在）
     */
    public static void ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
