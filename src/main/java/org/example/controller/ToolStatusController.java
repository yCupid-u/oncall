package org.example.controller;

import lombok.Data;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolStatusController {

    private final ToolCallbackProvider toolCallbackProvider;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean mcpClientEnabled;

    public ToolStatusController(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @GetMapping
    public ToolStatusResponse listTools() {
        ToolStatusResponse response = new ToolStatusResponse();
        response.setMcpClientEnabled(mcpClientEnabled);
        response.setLocalTools(List.of(
                DateTimeTools.class.getSimpleName(),
                InternalDocsTools.TOOL_QUERY_INTERNAL_DOCS,
                QueryMetricsTools.TOOL_QUERY_PROMETHEUS_ALERTS,
                QueryLogsTools.TOOL_GET_AVAILABLE_LOG_TOPICS,
                QueryLogsTools.TOOL_QUERY_LOGS
        ));

        List<ToolInfo> externalTools = new ArrayList<>();
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            ToolInfo info = new ToolInfo();
            info.setName(callback.getToolDefinition().name());
            info.setDescription(callback.getToolDefinition().description());
            externalTools.add(info);
        }
        response.setMcpTools(externalTools);
        response.setMcpToolCount(externalTools.size());
        return response;
    }

    @Data
    public static class ToolStatusResponse {
        private boolean mcpClientEnabled;
        private int mcpToolCount;
        private List<String> localTools;
        private List<ToolInfo> mcpTools;
    }

    @Data
    public static class ToolInfo {
        private String name;
        private String description;
    }
}
