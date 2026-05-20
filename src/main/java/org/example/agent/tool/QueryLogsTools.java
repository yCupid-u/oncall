package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.cls.v20201016.models.LogInfo;
import com.tencentcloudapi.cls.v20201016.models.SearchLogRequest;
import com.tencentcloudapi.cls.v20201016.models.SearchLogResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import lombok.Data;
import org.example.config.ClsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class QueryLogsTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryLogsTools.class);

    public static final String TOOL_QUERY_LOGS = "queryLogs";
    public static final String TOOL_GET_AVAILABLE_LOG_TOPICS = "getAvailableLogTopics";

    private static final List<String> VALID_REGIONS = List.of(
            "ap-guangzhou", "ap-shanghai", "ap-beijing", "ap-chengdu"
    );
    private static final Set<String> KNOWN_LOG_TOPICS = Set.of(
            "system-metrics", "application-logs", "database-slow-query", "system-events"
    );
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClsProperties clsProperties;

    public QueryLogsTools(ClsProperties clsProperties) {
        this.clsProperties = clsProperties;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("QueryLogsTools initialized. mockEnabled={}, defaultRegion={}, configuredTopics={}",
                clsProperties.isMockEnabled(), clsProperties.getDefaultRegion(), clsProperties.getTopicIds().keySet());
    }

    @Tool(description = "Get all available log topics and their descriptions before querying logs.")
    public String getAvailableLogTopics() {
        try {
            List<LogTopicInfo> topics = new ArrayList<>();
            topics.add(topic("system-metrics", "System metrics logs: CPU, memory, disk, load.",
                    List.of("cpu_usage:>80", "memory_usage:>85", "disk_usage:>90"),
                    List.of("HighCPUUsage", "HighMemoryUsage", "HighDiskUsage")));
            topics.add(topic("application-logs", "Application logs: errors, slow requests, dependencies.",
                    List.of("level:ERROR", "response_time:>3000", "downstream OR redis OR database"),
                    List.of("ServiceUnavailable", "SlowResponse", "HighMemoryUsage")));
            topics.add(topic("database-slow-query", "Database slow query logs.",
                    List.of("query_time:>2", "full_table_scan:true", "*"),
                    List.of("SlowResponse", "ServiceUnavailable")));
            topics.add(topic("system-events", "System events: pod restart, OOM kill, crash.",
                    List.of("restart OR crash", "oom_kill", "reason:OOMKilled"),
                    List.of("ServiceUnavailable", "HighMemoryUsage")));

            LogTopicsOutput output = new LogTopicsOutput();
            output.setSuccess(true);
            output.setTopics(topics);
            output.setAvailableRegions(VALID_REGIONS);
            output.setDefaultRegion(clsProperties.getDefaultRegion());
            output.setMessage("Use queryLogs with one of the returned log topics. Real mode requires Tencent CLS topic ids.");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("Failed to build log topic list", e);
            return "{\"success\":false,\"message\":\"failed to build log topic list\"}";
        }
    }

    @Tool(description = "Query logs from Tencent Cloud CLS or local mock logs. Parameters: region, logTopic, query, limit.")
    public String queryLogs(
            @ToolParam(description = "Region, for example ap-guangzhou. Defaults to configured cls.default-region.") String region,
            @ToolParam(description = "Log topic: system-metrics, application-logs, database-slow-query, system-events, or configured CLS topic id alias.") String logTopic,
            @ToolParam(description = "CLS query string. Empty value uses a default query for the log topic.") String query,
            @ToolParam(description = "Result size. Default 20, max 100.") Integer limit) {
        try {
            String safeRegion = normalizeRegion(region);
            if (safeRegion == null) {
                return buildErrorResponse("Unsupported region: " + region + ". Valid regions: " + VALID_REGIONS);
            }

            String safeTopic = normalizeLogTopic(logTopic);
            if (safeTopic == null) {
                return buildErrorResponse("logTopic is required. Call getAvailableLogTopics first.");
            }

            int actualLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
            String safeQuery = normalizeQuery(safeTopic, query);
            List<LogEntry> entries = clsProperties.isMockEnabled()
                    ? buildMockLogs(safeTopic, safeQuery, actualLimit)
                    : fetchTencentClsLogs(safeRegion, safeTopic, safeQuery, actualLimit);

            QueryLogsOutput output = new QueryLogsOutput();
            output.setSuccess(!entries.isEmpty());
            output.setMockMode(clsProperties.isMockEnabled());
            output.setRegion(safeRegion);
            output.setLogTopic(safeTopic);
            output.setQuery(safeQuery);
            output.setLogs(entries);
            output.setTotal(entries.size());
            output.setMessage(entries.isEmpty() ? "No matching logs found." : "Query returned " + entries.size() + " logs.");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("queryLogs failed", e);
            return buildErrorResponse("queryLogs failed: " + e.getMessage());
        }
    }

    private LogTopicInfo topic(String name, String description, List<String> examples, List<String> alerts) {
        LogTopicInfo info = new LogTopicInfo();
        info.setTopicName(name);
        info.setDescription(description);
        info.setExampleQueries(examples);
        info.setRelatedAlerts(alerts);
        return info;
    }

    private String normalizeRegion(String region) {
        String safeRegion = StringUtils.hasText(region) ? region.trim() : clsProperties.getDefaultRegion();
        return VALID_REGIONS.contains(safeRegion) ? safeRegion : null;
    }

    private String normalizeLogTopic(String logTopic) {
        if (!StringUtils.hasText(logTopic)) {
            return null;
        }
        return logTopic.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeQuery(String logTopic, String query) {
        if (StringUtils.hasText(query)) {
            return query.trim();
        }
        return switch (logTopic) {
            case "system-metrics" -> "cpu_usage:>80 OR memory_usage:>85 OR disk_usage:>90";
            case "application-logs" -> "level:ERROR OR level:WARN OR response_time:>3000";
            case "database-slow-query" -> "query_time:>2 OR full_table_scan:true";
            case "system-events" -> "restart OR crash OR oom_kill";
            default -> "*";
        };
    }

    private List<LogEntry> fetchTencentClsLogs(String region, String logTopic, String query, int limit)
            throws TencentCloudSDKException {
        String topicId = resolveTopicId(logTopic);
        if (!StringUtils.hasText(topicId)) {
            throw new IllegalStateException("CLS topicId is not configured for logTopic=" + logTopic
                    + ". Set TENCENT_CLS_TOPIC_" + logTopic.toUpperCase(Locale.ROOT).replace("-", "_")
                    + " or TENCENT_CLS_TOPIC_DEFAULT.");
        }
        if (!StringUtils.hasText(clsProperties.getSecretId()) || !StringUtils.hasText(clsProperties.getSecretKey())) {
            throw new IllegalStateException("Tencent Cloud credentials are missing. Set TENCENTCLOUD_SECRET_ID and TENCENTCLOUD_SECRET_KEY.");
        }

        Credential credential = new Credential(clsProperties.getSecretId(), clsProperties.getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(clsProperties.getEndpoint());
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        ClsClient client = new ClsClient(credential, region, clientProfile);
        long to = Instant.now().toEpochMilli();
        long from = to - Math.max(clsProperties.getDefaultLookbackMinutes(), 1) * 60 * 1000;

        SearchLogRequest request = new SearchLogRequest();
        request.setTopicId(topicId);
        request.setFrom(from);
        request.setTo(to);
        request.setQuery(query);
        request.setLimit((long) limit);
        request.setSort("desc");
        request.setUseNewAnalysis(true);

        SearchLogResponse response = client.SearchLog(request);
        List<LogEntry> entries = new ArrayList<>();
        LogInfo[] results = response.getResults();
        if (results == null) {
            return entries;
        }

        for (LogInfo result : results) {
            Map<String, String> fields = extractFields(result);
            LogEntry entry = new LogEntry();
            long eventTime = result.getTime() == null ? to : result.getTime();
            Instant eventInstant = eventTime > 10_000_000_000L
                    ? Instant.ofEpochMilli(eventTime)
                    : Instant.ofEpochSecond(eventTime);
            entry.setTimestamp(FORMATTER.format(eventInstant));
            entry.setLevel(firstNonBlank(fields.get("level"), fields.get("severity"), fields.get("log_level"), "INFO"));
            entry.setService(firstNonBlank(fields.get("service"), result.getTopicName(), logTopic));
            entry.setInstance(firstNonBlank(fields.get("instance"), result.getHostName(), result.getSource(), result.getTopicId()));
            entry.setMessage(firstNonBlank(fields.get("message"), fields.get("msg"), fields.get("content"), result.getRawLog(), result.getLogJson(), ""));
            entry.setMetrics(fields);
            entries.add(entry);
        }
        logger.info("Tencent CLS query completed. region={}, topic={}, topicId={}, returned={}, requestId={}",
                region, logTopic, topicId, entries.size(), response.getRequestId());
        return entries;
    }

    private String resolveTopicId(String logTopic) {
        String topicId = clsProperties.getTopicIds().get(logTopic);
        if (!StringUtils.hasText(topicId)) {
            topicId = clsProperties.getTopicIds().get("default");
        }
        return topicId;
    }

    private Map<String, String> extractFields(LogInfo result) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (StringUtils.hasText(result.getLogJson())) {
            try {
                Map<String, Object> values = objectMapper.readValue(result.getLogJson(), new TypeReference<>() {});
                values.forEach((key, value) -> fields.put(key, value == null ? "" : String.valueOf(value)));
            } catch (Exception e) {
                fields.put("log_json", result.getLogJson());
            }
        }
        putIfPresent(fields, "topic_id", result.getTopicId());
        putIfPresent(fields, "topic_name", result.getTopicName());
        putIfPresent(fields, "source", result.getSource());
        putIfPresent(fields, "file_name", result.getFileName());
        putIfPresent(fields, "host_name", result.getHostName());
        putIfPresent(fields, "raw_log", result.getRawLog());
        return fields;
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (StringUtils.hasText(value)) {
            fields.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private List<LogEntry> buildMockLogs(String logTopic, String query, int limit) {
        List<LogEntry> logs = new ArrayList<>();
        Instant now = Instant.now();
        switch (logTopic) {
            case "system-metrics" -> {
                logs.add(log(now, "WARN", "payment-service", "pod-payment-01",
                        "CPU usage is high: 92.0%, process java, threads 245",
                        Map.of("cpu_usage", "92.0", "alert", "HighCPUUsage")));
                logs.add(log(now.minus(3, ChronoUnit.MINUTES), "WARN", "order-service", "pod-order-01",
                        "Memory usage is high: 91.0%, heap 3.8GB/4GB",
                        Map.of("memory_usage", "91.0", "alert", "HighMemoryUsage")));
            }
            case "application-logs" -> {
                logs.add(log(now.minus(5, ChronoUnit.MINUTES), "ERROR", "order-service", "pod-order-01",
                        "Database connection pool exhausted, active 50/50, waiting 23",
                        Map.of("error_type", "ConnectionPoolExhaustedException")));
                logs.add(log(now.minus(6, ChronoUnit.MINUTES), "WARN", "user-service", "pod-user-01",
                        "Slow request /api/v1/users/profile, response_time=4200ms",
                        Map.of("response_time_ms", "4200", "alert", "SlowResponse")));
            }
            case "database-slow-query" -> logs.add(log(now.minus(4, ChronoUnit.MINUTES), "WARN", "mysql", "mysql-primary-01",
                    "Slow query SELECT * FROM orders, query_time=3.2s, rows_examined=1245678",
                    Map.of("query_time_sec", "3.2", "table", "orders")));
            case "system-events" -> logs.add(log(now.minus(15, ChronoUnit.MINUTES), "ERROR", "kubernetes", "node-worker-02",
                    "Pod restart: pod-order-01, reason OOMKilled, exit_code=137",
                    Map.of("event_type", "PodRestart", "reason", "OOMKilled")));
            default -> logs.add(log(now, "INFO", "generic-service", "instance-0",
                    "Generic log for query: " + query, new HashMap<>()));
        }
        return logs.size() > limit ? new ArrayList<>(logs.subList(0, limit)) : logs;
    }

    private LogEntry log(Instant time, String level, String service, String instance, String message, Map<String, String> metrics) {
        LogEntry entry = new LogEntry();
        entry.setTimestamp(FORMATTER.format(time));
        entry.setLevel(level);
        entry.setService(service);
        entry.setInstance(instance);
        entry.setMessage(message);
        entry.setMetrics(metrics);
        return entry;
    }

    private String buildErrorResponse(String message) {
        try {
            QueryLogsOutput output = new QueryLogsOutput();
            output.setSuccess(false);
            output.setMessage(message);
            output.setLogs(List.of());
            output.setTotal(0);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return String.format("{\"success\":false,\"message\":\"%s\"}", message);
        }
    }

    @Data
    public static class LogEntry {
        @JsonProperty("timestamp")
        private String timestamp;
        @JsonProperty("level")
        private String level;
        @JsonProperty("service")
        private String service;
        @JsonProperty("instance")
        private String instance;
        @JsonProperty("message")
        private String message;
        @JsonProperty("metrics")
        private Map<String, String> metrics;
    }

    @Data
    public static class QueryLogsOutput {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("mock_mode")
        private boolean mockMode;
        @JsonProperty("region")
        private String region;
        @JsonProperty("log_topic")
        private String logTopic;
        @JsonProperty("query")
        private String query;
        @JsonProperty("logs")
        private List<LogEntry> logs;
        @JsonProperty("total")
        private int total;
        @JsonProperty("message")
        private String message;
    }

    @Data
    public static class LogTopicInfo {
        @JsonProperty("topic_name")
        private String topicName;
        @JsonProperty("description")
        private String description;
        @JsonProperty("example_queries")
        private List<String> exampleQueries;
        @JsonProperty("related_alerts")
        private List<String> relatedAlerts;
    }

    @Data
    public static class LogTopicsOutput {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("topics")
        private List<LogTopicInfo> topics;
        @JsonProperty("available_regions")
        private List<String> availableRegions;
        @JsonProperty("default_region")
        private String defaultRegion;
        @JsonProperty("message")
        private String message;
    }
}
