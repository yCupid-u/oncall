package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Shared ReactAgent chat service.
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    public String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的智能运维助手，可以回答普通问题，也可以按需调用工具。\n");
        prompt.append("当用户询问当前时间时，使用 getCurrentDateTime 工具。\n");
        prompt.append("当用户需要查询内部文档、流程、故障手册或技术指南时，使用 queryInternalDocs 工具。\n");
        prompt.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");

        int externalToolCount = getToolCallbacks().length;
        if (externalToolCount > 0) {
            prompt.append("当用户需要查询腾讯云日志、CMDB、工单等外部系统时，优先使用已注入的 MCP 工具；");
            prompt.append("如果 MCP 工具不可用，再使用本地 queryLogs 工具。默认地域是 ap-guangzhou。\n");
        } else {
            prompt.append("当用户需要查询日志时，使用本地 queryLogs 工具；生产环境可以通过 MCP 注入真实日志工具。");
            prompt.append("默认地域是 ap-guangzhou。\n");
        }
        prompt.append("当前可用 MCP 工具数量: ").append(externalToolCount).append("。\n\n");

        if (!history.isEmpty()) {
            prompt.append("--- 对话历史 ---\n");
            for (Map<String, String> message : history) {
                String role = message.get("role");
                String content = message.get("content");
                if ("user".equals(role)) {
                    prompt.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    prompt.append("助手: ").append(content).append("\n");
                }
            }
            prompt.append("--- 对话历史结束 ---\n\n");
        }

        prompt.append("请基于以上上下文回答用户的新问题。涉及运维排障时，先给证据，再给结论；证据不足时直接说明不足。");
        return prompt.toString();
    }

    public Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        }
        return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
    }

    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        logger.info("Available MCP tools: {}", toolCallbacks.length);
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("Executing ReactAgent.call()");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent chat completed, answerLength={}", answer.length());
        return answer;
    }
}
