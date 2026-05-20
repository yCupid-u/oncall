package org.example.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void writesDefaultAiOpsMemoriesAndFormatsHits() {
        JsonMemoryStore store = new JsonMemoryStore(new ObjectMapper(), tempDir.resolve("memory-store.json"));
        MemoryService service = new MemoryService(store, new MemoryFormatter());

        service.writeAiOpsDefaults(
                "HighCPUUsage HighMemoryUsage SlowResponse",
                "CPU usage is high: 92.0%",
                "reason OOMKilled",
                "Slow SQL SELECT_ORDER_BY_USER"
        );

        List<MemoryRecord> hits = service.searchAiOpsDefaults();
        String markdown = service.formatHits(hits);

        assertThat(hits).hasSize(3);
        assertThat(markdown).contains("payment-service");
        assertThat(markdown).contains("HighCPUUsage");
        assertThat(markdown).contains("历史根因");
    }
}
