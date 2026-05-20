package org.example.memory;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryService {

    private final MemoryStore memoryStore;
    private final MemoryFormatter formatter;

    public MemoryService(MemoryStore memoryStore, MemoryFormatter formatter) {
        this.memoryStore = memoryStore;
        this.formatter = formatter;
    }

    public List<MemoryRecord> findAll() {
        return memoryStore.findAll();
    }

    public MemoryRecord rememberIncident(String serviceName, String alertName, String rootCause,
                                         String action, String summary, String evidence) {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryType(MemoryType.INCIDENT_SUMMARY);
        record.setSource("ai_ops_report");
        record.setSessionId("aiops-default");
        record.setServiceName(serviceName);
        record.setAlertName(alertName);
        record.setEntityKey(serviceName + ":" + alertName);
        record.setRootCause(rootCause);
        record.setAction(action);
        record.setSummary(summary);
        record.setContent(summary);
        record.setEvidence(evidence);
        record.setConfidence(0.8);
        return memoryStore.upsert(record);
    }

    public List<MemoryRecord> search(MemorySearchRequest request) {
        return memoryStore.search(request);
    }

    public List<MemoryRecord> searchAiOpsDefaults() {
        Map<String, MemoryRecord> merged = new LinkedHashMap<>();
        for (MemorySearchRequest request : defaultAiOpsRequests()) {
            for (MemoryRecord record : memoryStore.search(request)) {
                merged.put(record.getMemoryId(), record);
            }
        }
        return new ArrayList<>(merged.values());
    }

    public String formatHits(List<MemoryRecord> records) {
        return formatter.toMarkdownTable(records);
    }

    public List<MemoryRecord> writeAiOpsDefaults(String alerts, String applicationLogs,
                                                 String systemEvents, String databaseSlowQueryLogs) {
        List<MemoryRecord> records = new ArrayList<>();
        records.add(rememberIncident(
                "payment-service",
                "HighCPUUsage",
                "CPU 使用率过高，可能导致接口慢响应。",
                "检查热点线程和实例负载，保留现场后扩容或重启实例。",
                "payment-service 触发 HighCPUUsage，日志中出现 CPU usage is high。",
                firstEvidence(alerts, applicationLogs)
        ));
        records.add(rememberIncident(
                "order-service",
                "HighMemoryUsage",
                "内存使用率过高，历史证据中可能伴随 OOMKilled 或连接池耗尽。",
                "检查 JVM 堆、连接池和最近重启事件，必要时扩容或重启。",
                "order-service 触发 HighMemoryUsage，system-events 可用于确认是否发生 OOMKilled。",
                firstEvidence(alerts, systemEvents)
        ));
        records.add(rememberIncident(
                "user-service",
                "SlowResponse",
                "慢响应可能与下游服务延迟或数据库慢查询有关。",
                "优先检查慢 SQL、连接池和下游 order-service 延迟。",
                "user-service 触发 SlowResponse，database-slow-query 可用于确认慢 SQL。",
                firstEvidence(alerts, databaseSlowQueryLogs)
        ));
        return records;
    }

    public void clear() {
        memoryStore.clear();
    }

    private List<MemorySearchRequest> defaultAiOpsRequests() {
        return List.of(
                new MemorySearchRequest("payment-service", "HighCPUUsage", "CPU payment-service", 3),
                new MemorySearchRequest("order-service", "HighMemoryUsage", "memory order-service OOMKilled", 3),
                new MemorySearchRequest("user-service", "SlowResponse", "slow response user-service SQL", 3)
        );
    }

    private String firstEvidence(String primary, String secondary) {
        String source = (secondary != null && !secondary.isBlank()) ? secondary : primary;
        if (source == null || source.isBlank()) {
            return "未采集到可用证据。";
        }
        int maxLength = Math.min(500, source.length());
        return source.substring(0, maxLength);
    }
}
