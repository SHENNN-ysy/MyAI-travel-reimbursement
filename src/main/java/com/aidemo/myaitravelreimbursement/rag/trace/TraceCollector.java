package com.aidemo.myaitravelreimbursement.rag.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 追踪收集器，异步写入 traces.jsonl。
 * <p>
 * 请求结束后，将本次 RAG 追踪数据写入日志文件：
 * logs/traces.jsonl（每行一条 JSON 记录）。
 */
@Slf4j
@Component
public class TraceCollector {

    private static final Path TRACE_FILE = Paths.get("logs/traces.jsonl");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;
    private final BlockingQueue<TraceRecord> queue;
    private final ExecutorService writerExecutor;
    private volatile boolean running = true;

    public TraceCollector() {
        this.objectMapper = new ObjectMapper()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.queue = new LinkedBlockingQueue<>(1024);
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "trace-writer");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        // 确保 logs 目录存在
        try {
            Files.createDirectories(TRACE_FILE.getParent());
        } catch (IOException e) {
            log.warn("Failed to create logs directory", e);
        }
        // 启动异步写入线程
        writerExecutor.submit(this::flushLoop);
        log.info("TraceCollector 已启动，写入文件: {}", TRACE_FILE);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 记录一次完整的 RAG 追踪。
     */
    public void record(TraceRecord record) {
        if (!queue.offer(record)) {
            log.warn("Trace queue full, dropping record: traceId={}", record.traceId());
        }
    }

    private void flushLoop() {
        List<TraceRecord> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                TraceRecord record = queue.poll(1, TimeUnit.SECONDS);
                if (record != null) {
                    batch.add(record);
                    if (batch.size() >= 10 || queue.isEmpty()) {
                        flushBatch(batch);
                        batch = new ArrayList<>();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 最后一批
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }

    private void flushBatch(List<TraceRecord> batch) {
        try (BufferedWriter writer = Files.newBufferedWriter(TRACE_FILE,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (TraceRecord record : batch) {
                String json = objectMapper.writeValueAsString(record.toMap());
                writer.write(json);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to write traces", e);
        }
    }

    // ---------- Record 类型 ----------

    public record TraceRecord(
            String traceId,
            String timestamp,
            String query,
            int retrievedCount,
            List<String> sources,
            String responseSummary,
            long elapsedMs,
            Map<String, Object> metadata
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "traceId", traceId,
                    "timestamp", timestamp,
                    "query", query,
                    "retrievedCount", retrievedCount,
                    "sources", sources,
                    "responseSummary", responseSummary != null ? responseSummary : "",
                    "elapsedMs", elapsedMs,
                    "metadata", metadata != null ? metadata : Map.of()
            );
        }
    }

    // ---------- Builder ----------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String traceId;
        private String query;
        private int retrievedCount;
        private List<String> sources = List.of();
        private String responseSummary;
        private long elapsedMs;
        private Map<String, Object> metadata = Map.of();

        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder query(String query) { this.query = query; return this; }
        public Builder retrievedCount(int n) { this.retrievedCount = n; return this; }
        public Builder sources(List<String> s) { this.sources = s; return this; }
        public Builder responseSummary(String s) { this.responseSummary = s; return this; }
        public Builder elapsedMs(long ms) { this.elapsedMs = ms; return this; }
        public Builder metadata(Map<String, Object> m) { this.metadata = m; return this; }

        public TraceRecord build() {
            return new TraceRecord(
                    traceId,
                    LocalDateTime.now().format(FORMATTER),
                    query,
                    retrievedCount,
                    sources,
                    responseSummary,
                    elapsedMs,
                    metadata
            );
        }
    }
}
