package org.example.service;

import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.config.ClsProperties;
import org.example.memory.MemoryFormatter;
import org.example.memory.MemoryService;
import org.example.memory.MemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiOpsServiceTest {

    @Test
    void directAiOpsReportContainsControlledAgentTraceAndEvidence() {
        AiOpsService service = new AiOpsService();
        ReflectionTestUtils.setField(service, "queryMetricsTools", new StubQueryMetricsTools());
        ReflectionTestUtils.setField(service, "internalDocsTools", new StubInternalDocsTools());
        ReflectionTestUtils.setField(service, "queryLogsTools", new StubQueryLogsTools());
        InMemoryMemoryStore memoryStore = new InMemoryMemoryStore();
        ReflectionTestUtils.setField(service, "memoryService", new MemoryService(memoryStore, new MemoryFormatter()));

        String report = service.executeDirectAiOpsReport();

        assertThat(report).contains("# 告警分析报告");
        assertThat(report).contains("## Planner-Executor-Replan 执行轨迹");
        assertThat(report).contains("规划");
        assertThat(report).contains("执行");
        assertThat(report).contains("重规划");
        assertThat(report).contains("最终报告");
        assertThat(report).contains("## 历史记忆命中");
        assertThat(report).contains("HighCPUUsage");
        assertThat(report).contains("CLS query returned 2 logs");
        assertThat(memoryStore.findAll()).hasSize(3);
    }

    private static class StubQueryMetricsTools extends QueryMetricsTools {
        @Override
        public String queryPrometheusAlerts() {
            return """
                    {"success":true,"alerts":[{"alert_name":"HighCPUUsage","state":"firing"}]}
                    """;
        }
    }

    private static class StubInternalDocsTools extends InternalDocsTools {
        StubInternalDocsTools() {
            super(null);
        }

        @Override
        public String queryInternalDocs(String query) {
            return """
                    {"success":true,"results":[{"content":"CPU high usage mitigation runbook"}]}
                    """;
        }
    }

    private static class StubQueryLogsTools extends QueryLogsTools {
        StubQueryLogsTools() {
            super(new ClsProperties());
        }

        @Override
        public String queryLogs(String region, String logTopic, String query, Integer limit) {
            return """
                    {"success":true,"total":2,"message":"CLS query returned 2 logs","logs":[{"message":"CPU usage is high"}]}
                    """;
        }
    }

    private static class InMemoryMemoryStore implements MemoryStore {
        private final List<org.example.memory.MemoryRecord> records = new ArrayList<>();

        @Override
        public List<org.example.memory.MemoryRecord> findAll() {
            return new ArrayList<>(records);
        }

        @Override
        public org.example.memory.MemoryRecord upsert(org.example.memory.MemoryRecord record) {
            records.removeIf(existing -> existing.getEntityKey().equals(record.getEntityKey()));
            records.add(record);
            return record;
        }

        @Override
        public List<org.example.memory.MemoryRecord> search(org.example.memory.MemorySearchRequest request) {
            return records.stream()
                    .filter(record -> request.getAlertName() != null && request.getAlertName().equals(record.getAlertName()))
                    .toList();
        }

        @Override
        public void clear() {
            records.clear();
        }
    }
}
