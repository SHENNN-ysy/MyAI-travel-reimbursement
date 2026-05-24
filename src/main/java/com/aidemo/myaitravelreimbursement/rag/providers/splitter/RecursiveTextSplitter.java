package com.aidemo.myaitravelreimbursement.rag.providers.splitter;

import com.aidemo.myaitravelreimbursement.rag.types.Chunk;
import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Markdown 标题的递归分块器。
 * <p>
 * 按标题层级从粗到细递归切分：
 * <ol>
 *   <li>优先级 1（最粗）：## 一级标题，如 "## 基本操作"</li>
 *   <li>优先级 2：### 二级标题，如 "### 发起咨询"</li>
 *   <li>优先级 3（最细）：\n 换行符</li>
 * </ol>
 * <p>
 * 每个 Heading 作为 chunk 的起始标记，块内文本优先保持完整的标题+内容，
 * 只有当单个 Heading 下的内容仍超长时，才退到下一级分隔符递归切分。
 * <p>
 * 配置项：rag.chunking.max-chunk-size、rag.chunking.overlap
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecursiveTextSplitter implements TextSplitter {

    private final RagProperties ragProperties;

    /**
     * 三级标题分隔符正则表达式：
     * (?m)   — 多行模式，使 ^ 匹配行首
     * ^##\\s+ — 行首的 ## 加至少一个空白字符
     */
    private static final String H1_REGEX = "(?m)^##\\s+";
    private static final String H2_REGEX = "(?m)^###\\s+";
    private static final String NL_REGEX = "\\n";

    private static final Pattern H1_PATTERN = Pattern.compile(H1_REGEX);
    private static final Pattern H2_PATTERN = Pattern.compile(H2_REGEX);
    private static final Pattern NL_PATTERN = Pattern.compile(NL_REGEX);

    @Override
    public List<Chunk> split(String text, String fileName, String domain) {
        int maxSize = ragProperties.getChunking().getMaxChunkSize();

        // ① 按 ## 标题拆分出 H1 section
        List<Section> h1Sections = splitByHeading(text, H1_PATTERN, maxSize);

        List<Chunk> result = new ArrayList<>();
        int index = 0;

        for (Section h1Sec : h1Sections) {
            // ② 每个 H1 section 内部，按 ### 标题拆分
            List<Section> h2Sections = splitByHeading(h1Sec.text, H2_PATTERN, maxSize);

            for (Section h2Sec : h2Sections) {
                // ③ 每个 H2 section 内部，按换行符递归拆分
                List<String> chunks = recursiveCharSplit(h2Sec.text, maxSize);

                for (String chunkText : chunks) {
                    if (chunkText.trim().isEmpty()) continue;

                    // 拼装完整 chunk：标题前缀 + 内容
                    // 当 h2Sec.heading 为空时（纯描述段落），自动归属到父级 H1
                    String fullText;
                    if (h2Sec.heading.isEmpty()) {
                        // 无 H2 时：仅拼 H1 前缀（描述文字直接作为 H1 下的内容）
                        // chunkText 已包含 ## 标题行（H1 行随正文一起被递归保留），
                        // 需去重避免 H1 行被重复拼接
                        String h1Heading = h1Sec.heading.orElse("");
                        if (h1Heading.length() > 0 && chunkText.startsWith(h1Heading)) {
                            fullText = chunkText;
                        } else {
                            fullText = h1Sec.heading.map(h -> h + "\n" + chunkText).orElse(chunkText);
                        }
                    } else {
                        // 有 H2 时：chunkText 已包含 ### 标题行（H2 行随正文一起被递归保留），
                        // 只需追加内容部分（避免 H2 行被重复拼接）
                        String h2Heading = h2Sec.heading.orElse("");
                        if (chunkText.startsWith(h2Heading)) {
                            fullText = h2Heading + "\n" + chunkText.substring(h2Heading.length()).trim();
                        } else {
                            fullText = h2Heading + "\n" + chunkText;
                        }
                    }

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("source_file", fileName);
                    metadata.put("domain", domain);
                    metadata.put("chunk_index", String.valueOf(index));
                    h1Sec.heading.ifPresent(h -> metadata.put("h1_heading", h));
                    h2Sec.heading.ifPresent(h -> metadata.put("h2_heading", h));

                    result.add(new Chunk(fullText, metadata));
                    index++;
                }
            }
        }

        log.debug("Split file '{}' into {} chunks (maxSize={})", fileName, result.size(), maxSize);
        return result;
    }

    /**
     * 按指定标题级别（Pattern）切分文本，返回每个 section（标题+内容）。
     * <p>
     * 每个 Section 包含：
     * - heading：标题文本（不含 ## 前缀），如 "基本操作"
     * - text：该标题下的正文内容（不含标题行）
     * <p>
     * 如果某个 section 超长（正文超过 maxSize），则递归退到下一级切分符。
     */
    private List<Section> splitByHeading(String text, Pattern headingPattern, int maxSize) {
        List<Section> sections = new ArrayList<>();
        if (text == null || text.isEmpty()) return sections;

        Matcher matcher = headingPattern.matcher(text);
        List<Integer> positions = new ArrayList<>();
        List<String> headings = new ArrayList<>();

        // 收集所有匹配的起始位置和标题文本
        while (matcher.find()) {
            positions.add(matcher.start());
            // 截取标题行：从 matchStart 到该行行尾
            int lineEnd = findLineEnd(text, matcher.start());
            String rawTitle = text.substring(matcher.start(), lineEnd).trim();
            headings.add(rawTitle);
        }

        // 无标题时，把整段文本作为一个无标题 section
        if (positions.isEmpty()) {
            for (String chunk : recursiveCharSplit(text, maxSize)) {
                if (!chunk.trim().isEmpty()) {
                    sections.add(new Section(java.util.Optional.empty(), chunk));
                }
            }
            return sections;
        }

        // 遍历每个标题，计算对应的正文范围
        int count = positions.size();
        for (int i = 0; i < count; i++) {
            int headingStart = positions.get(i);
            int bodyStart = headingStart; // 标题行作为 body 的第一行保留，不排除
            int bodyEnd; // 内容结尾（下一标题行首 或 文本末尾）

            if (i + 1 < count) {
                bodyEnd = positions.get(i + 1);
            } else {
                bodyEnd = text.length();
            }

            String heading = headings.get(i);
            String body = text.substring(bodyStart, bodyEnd).trim();

            if (body.isEmpty()) continue;

            if (body.length() <= maxSize) {
                // 正常：一个 section 对应一个 chunk
                sections.add(new Section(java.util.Optional.of(heading), body));
            } else {
                // 超长：递归退到下一级切分符
                String nextSeparator = nextSeparator(headingPattern);
                Pattern nextPattern = nextSeparator.equals(H1_REGEX) ? H1_PATTERN
                        : nextSeparator.equals(H2_REGEX) ? H2_PATTERN : NL_PATTERN;
                List<Section> subSections = splitByHeading(body, nextPattern, maxSize);

                if (!subSections.isEmpty() && subSections.get(0).heading().isEmpty()) {
                    // 第一个子 section 没有标题，说明有打头的纯描述文字（无 H2/H3 标题前缀）。
                    // 将其归属于当前层级的 heading，避免描述段落丢失。
                    Section first = subSections.get(0);
                    Section tagged = new Section(
                            java.util.Optional.of(heading),
                            first.text()
                    );
                    subSections = new java.util.ArrayList<>(subSections);
                    subSections.set(0, tagged);
                }

                for (Section sub : subSections) {
                    // 子 section 保留当前层级标题作为前缀
                    sections.add(new Section(java.util.Optional.of(heading), sub.text));
                }
            }
        }

        return sections;
    }

    /** 根据当前 headingPattern 返回下一级分隔符 */
    private String nextSeparator(Pattern current) {
        if (current == H1_PATTERN) return H2_REGEX;
        if (current == H2_PATTERN) return NL_REGEX;
        return NL_REGEX;
    }

    /** 找到行尾位置（下一个 \n 或文本末尾） */
    private int findLineEnd(String text, int from) {
        int nl = text.indexOf('\n', from);
        return nl < 0 ? text.length() : nl;
    }

    // ---------- 递归字符级切分（最细粒度 fallback） ----------

    /**
     * 递归字符级切分，按 \n → 空格 → 硬切 的顺序降级。
     * 用于处理单个标题下正文仍然超长的情况。
     */
    private List<String> recursiveCharSplit(String text, int maxSize) {
        if (text.isEmpty()) return List.of();
        return recursiveCharSplit(text, new String[]{"\n", " "}, maxSize);
    }

    private List<String> recursiveCharSplit(String text, String[] separators, int maxSize) {
        if (text.isEmpty()) return List.of();
        if (text.length() <= maxSize) return List.of(text);

        String currentSep = separators[0];
        String[] parts = text.split(Pattern.quote(currentSep), -1);

        if (parts.length <= 1) {
            // 没有该分隔符，退到下一级
            if (separators.length > 1) {
                return recursiveCharSplit(text, dropFirst(separators), maxSize);
            } else {
                return splitBySize(text, maxSize);
            }
        }

        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (buffer.length() == 0) {
                buffer.append(part);
            } else if (buffer.length() + currentSep.length() + part.length() <= maxSize) {
                buffer.append(currentSep).append(part);
            } else {
                if (buffer.length() > 0) {
                    merged.add(buffer.toString());
                }
                // overlap：取前 maxSize 字符作为下一个 chunk 的开头
                buffer = new StringBuilder(part.length() > maxSize
                        ? part.substring(0, maxSize) : part);
            }
        }

        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }

        // 超大 chunk 递归降级
        List<String> finalChunks = new ArrayList<>();
        for (String chunk : merged) {
            if (chunk.length() > maxSize && separators.length > 1) {
                finalChunks.addAll(recursiveCharSplit(chunk, dropFirst(separators), maxSize));
            } else {
                finalChunks.add(chunk);
            }
        }

        return finalChunks;
    }

    private String[] dropFirst(String[] arr) {
        String[] next = new String[arr.length - 1];
        System.arraycopy(arr, 1, next, 0, arr.length - 1);
        return next;
    }

    private List<String> splitBySize(String text, int maxSize) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxSize) {
            parts.add(text.substring(i, Math.min(i + maxSize, text.length())));
        }
        return parts;
    }

    // ---------- 内部类型 ----------

    /**
     * 表示一个标题及其对应正文内容的 section。
     *
     * @param heading 标题文本（不含 ## 前缀），如 "基本操作"；无标题时为 empty
     * @param text    正文内容（不含标题行）
     */
    private record Section(java.util.Optional<String> heading, String text) {}
}
