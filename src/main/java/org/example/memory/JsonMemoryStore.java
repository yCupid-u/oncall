package org.example.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Repository
public class JsonMemoryStore implements MemoryStore {

    private final ObjectMapper objectMapper;
    private final Path storePath;

    public JsonMemoryStore(ObjectMapper objectMapper, AgentMemoryProperties properties) {
        this(objectMapper, Paths.get(properties.getPath()));
    }

    JsonMemoryStore(ObjectMapper objectMapper, Path storePath) {
        this.objectMapper = objectMapper.findAndRegisterModules();
        this.storePath = storePath;
    }

    @Override
    public synchronized List<MemoryRecord> findAll() {
        return new ArrayList<>(readRecords());
    }

    @Override
    public synchronized MemoryRecord upsert(MemoryRecord record) {
        List<MemoryRecord> records = readRecords();
        LocalDateTime now = LocalDateTime.now();
        normalize(record, now);

        for (int i = 0; i < records.size(); i++) {
            MemoryRecord existing = records.get(i);
            if (Objects.equals(existing.getEntityKey(), record.getEntityKey())) {
                record.setMemoryId(existing.getMemoryId());
                record.setCreatedAt(existing.getCreatedAt());
                record.setHitCount(existing.getHitCount());
                record.setLastHitAt(existing.getLastHitAt());
                records.set(i, record);
                writeRecords(records);
                return record;
            }
        }

        records.add(record);
        writeRecords(records);
        return record;
    }

    @Override
    public synchronized List<MemoryRecord> search(MemorySearchRequest request) {
        int limit = request.getLimit() <= 0 ? 3 : request.getLimit();
        List<ScoredMemory> scored = readRecords().stream()
                .map(record -> new ScoredMemory(record, score(record, request)))
                .filter(item -> item.score > 0)
                .sorted(Comparator.comparingInt(ScoredMemory::score).reversed()
                        .thenComparing(item -> item.record.getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        List<MemoryRecord> all = readRecords();
        List<MemoryRecord> hits = new ArrayList<>();
        for (ScoredMemory item : scored) {
            for (MemoryRecord stored : all) {
                if (Objects.equals(stored.getMemoryId(), item.record.getMemoryId())) {
                    stored.setHitCount(stored.getHitCount() + 1);
                    stored.setLastHitAt(now);
                    hits.add(stored);
                    break;
                }
            }
        }
        writeRecords(all);
        return hits;
    }

    @Override
    public synchronized void clear() {
        writeRecords(new ArrayList<>());
    }

    private int score(MemoryRecord record, MemorySearchRequest request) {
        int score = 0;
        if (same(record.getServiceName(), request.getServiceName())) {
            score += 4;
        }
        if (same(record.getAlertName(), request.getAlertName())) {
            score += 4;
        }
        String keyword = lower(request.getKeyword());
        if (!keyword.isBlank()) {
            String text = lower(record.getSummary()) + " " + lower(record.getContent()) + " "
                    + lower(record.getRootCause()) + " " + lower(record.getAction()) + " "
                    + lower(record.getEvidence());
            for (String token : keyword.split("\\s+")) {
                if (!token.isBlank() && text.contains(token)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private void normalize(MemoryRecord record, LocalDateTime now) {
        if (record.getMemoryId() == null || record.getMemoryId().isBlank()) {
            record.setMemoryId(buildMemoryId(record));
        }
        if (record.getEntityKey() == null || record.getEntityKey().isBlank()) {
            record.setEntityKey(buildEntityKey(record));
        }
        if (record.getSource() == null || record.getSource().isBlank()) {
            record.setSource("unknown");
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
    }

    private String buildMemoryId(MemoryRecord record) {
        return buildEntityKey(record).replaceAll("[^A-Za-z0-9:_-]", "_");
    }

    private String buildEntityKey(MemoryRecord record) {
        return safe(record.getServiceName()) + ":" + safe(record.getAlertName());
    }

    private List<MemoryRecord> readRecords() {
        try {
            if (!Files.exists(storePath)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(storePath.toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("读取记忆文件失败: " + storePath, e);
        }
    }

    private void writeRecords(List<MemoryRecord> records) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), records);
        } catch (IOException e) {
            throw new IllegalStateException("写入记忆文件失败: " + storePath, e);
        }
    }

    private boolean same(String left, String right) {
        return !lower(left).isBlank() && lower(left).equals(lower(right));
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private record ScoredMemory(MemoryRecord record, int score) {
    }
}
