package org.example.memory;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryFormatter {

    public String toMarkdownTable(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return "暂无历史记忆命中。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("| 服务 | 告警 | 历史根因 | 历史处理动作 | 命中次数 |\n");
        builder.append("|---|---|---|---|---|\n");
        for (MemoryRecord record : records) {
            builder.append("| ")
                    .append(value(record.getServiceName()))
                    .append(" | ")
                    .append(value(record.getAlertName()))
                    .append(" | ")
                    .append(value(record.getRootCause()))
                    .append(" | ")
                    .append(value(record.getAction()))
                    .append(" | ")
                    .append(record.getHitCount())
                    .append(" |\n");
        }
        return builder.toString();
    }

    private String value(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "/").replace("\n", " ");
    }
}
