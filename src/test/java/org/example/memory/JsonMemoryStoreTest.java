package org.example.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMemoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertsByEntityKeyAndSearchesByServiceAndAlert() {
        JsonMemoryStore store = new JsonMemoryStore(new ObjectMapper(), tempDir.resolve("memory-store.json"));

        MemoryRecord first = record("payment-service", "HighCPUUsage", "CPU 高", "扩容");
        store.upsert(first);

        MemoryRecord updated = record("payment-service", "HighCPUUsage", "CPU 高并伴随慢响应", "检查线程后扩容");
        store.upsert(updated);

        List<MemoryRecord> all = store.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getRootCause()).isEqualTo("CPU 高并伴随慢响应");

        List<MemoryRecord> hits = store.search(new MemorySearchRequest("payment-service", "HighCPUUsage", "CPU", 3));
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getHitCount()).isEqualTo(1);
        assertThat(store.findAll().get(0).getLastHitAt()).isNotNull();
    }

    private MemoryRecord record(String serviceName, String alertName, String rootCause, String action) {
        MemoryRecord record = new MemoryRecord();
        record.setSource("test");
        record.setServiceName(serviceName);
        record.setAlertName(alertName);
        record.setEntityKey(serviceName + ":" + alertName);
        record.setRootCause(rootCause);
        record.setAction(action);
        record.setSummary(serviceName + " " + alertName + " " + rootCause);
        record.setContent(record.getSummary());
        return record;
    }
}
