package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.memory.MemoryRecord;
import org.example.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    @Autowired(required = false)
    private MemoryService memoryService;

    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks)
            throws GraphRunnerException {
        logger.info("Starting AI Ops multi-agent orchestration");

        ReactAgent plannerAgent = buildPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildExecutorAgent(chatModel, toolCallbacks);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("ai_ops_supervisor")
                .description("Coordinates planner_agent and executor_agent for AIOps incident analysis")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = """
                You are an enterprise SRE on-call assistant.
                Run a bounded incident investigation using the available tools:
                1. Read current Prometheus alerts.
                2. Search internal runbooks and fault manuals.
                3. Query CLS logs only when needed.
                4. Summarize evidence, root cause, risk, and next actions.

                You may use historical memory as background, but current tool evidence has priority.
                Do not fabricate evidence. If a tool fails or returns no data, report that fact.
                Do not use alarm notification, webhook, shield, or notice-management tools.
                Finish with a Markdown report.
                """;

        return supervisorAgent.invoke(taskPrompt);
    }

    public String executeDirectAiOpsReport() {
        logger.info("Starting direct AI Ops evidence collection");

        List<MemoryRecord> memoryHits = memoryService == null ? List.of() : memoryService.searchAiOpsDefaults();
        String memoryMarkdown = memoryService == null ? "记忆服务未启用。" : memoryService.formatHits(memoryHits);

        String plannerStep = "规划：读取当前活跃告警，检索历史记忆、运维手册，并查询相关 CLS 日志主题。";
        String alerts = queryMetricsTools.queryPrometheusAlerts();
        String executorStep1 = "执行：调用 Prometheus 告警工具，获取当前活跃告警集合。";

        String docs = internalDocsTools.queryInternalDocs("HighCPUUsage HighMemoryUsage SlowResponse on-call runbook root cause mitigation");
        String executorStep2 = "执行：调用 RAG 知识库，检索 CPU、内存、慢响应相关处理手册。";

        String applicationLogs = "当前运行环境未加载 queryLogs 工具。";
        String systemEvents = "当前运行环境未加载 queryLogs 工具。";
        String databaseSlowQueryLogs = "当前运行环境未加载 queryLogs 工具。";
        if (queryLogsTools != null) {
            applicationLogs = queryLogsTools.queryLogs(
                    "ap-guangzhou",
                    "application-logs",
                    "ERROR OR SlowResponse OR HighCPUUsage OR HighMemoryUsage",
                    20
            );
            systemEvents = queryLogsTools.queryLogs(
                    "ap-guangzhou",
                    "system-events",
                    "restart OR crash OR oom_kill OR OOMKilled",
                    20
            );
            databaseSlowQueryLogs = queryLogsTools.queryLogs(
                    "ap-guangzhou",
                    "database-slow-query",
                    "SlowResponse OR query_time:>2 OR full_table_scan:true",
                    20
            );
        }

        if (memoryService != null) {
            memoryService.writeAiOpsDefaults(alerts, applicationLogs, systemEvents, databaseSlowQueryLogs);
        }

        String executorStep3 = "执行：查询 application-logs、system-events、database-slow-query 三个 CLS 主题，收集日志证据。";
        String replanStep = "重规划：合并历史记忆、告警、手册和日志证据；对缺失证据明确标注，不编造结论。";
        String finalStep = "最终报告：基于已收集证据生成根因分析、处理建议和后续跟进项，并写回长期记忆。";

        return """
                # 告警分析报告

                ## Planner-Executor-Replan 执行轨迹

                | 阶段 | 决策 / 动作 | 证据 |
                |---|---|---|
                | 规划 | %s | 需要历史记忆、告警清单、知识库手册和 CLS 日志检索结果。 |
                | 执行 | %s | Prometheus 告警工具输出见下方。 |
                | 执行 | %s | RAG 知识库检索结果见下方。 |
                | 执行 | %s | CLS 日志查询结果见下方。 |
                | 重规划 | %s | 空结果或缺失证据会保留在报告中。 |
                | 最终报告 | %s | 报告由已采集的证据生成。 |

                ## 历史记忆命中

                %s

                ## 活跃告警

                ```json
                %s
                ```

                ## 运维手册 / RAG 证据

                ```json
                %s
                ```

                ## 日志证据

                ### application-logs

                ```json
                %s
                ```

                ### system-events

                ```json
                %s
                ```

                ### database-slow-query

                ```json
                %s
                ```

                ## 根因分析

                本次 AIOps 排障已经通过项目内工具采集历史记忆、活跃告警、RAG 运维手册和 CLS 日志证据。
                历史记忆只作为背景经验，当前 Prometheus、CLS 和 RAG 工具返回的证据优先级更高。
                如果历史记忆与当前证据冲突，应以当前证据为准，并在报告中说明冲突点。

                ## 处理建议

                1. 根据告警标签确认受影响服务、Pod 和实例。
                2. 检查对应工作负载的 CPU、内存、响应时间和近期重启事件。
                3. 将历史记忆、CLS 日志证据与 RAG 手册中的处理步骤进行对照。
                4. 如果问题是资源饱和，先保留现场证据，再执行扩容、限流或重启实例。
                5. 如果问题指向慢依赖或数据库访问，优先检查慢 SQL、连接池和下游接口延迟。

                ## 风险与跟进

                在告警恢复且相同 CLS 查询不再返回新增错误日志前，建议保持事件处于跟进状态。
                本次报告会把服务、告警、根因和处理动作写入 JSON 长期记忆，用于后续相似告警召回。
                """.formatted(
                plannerStep,
                executorStep1,
                executorStep2,
                executorStep3,
                replanStep,
                finalStep,
                memoryMarkdown,
                alerts,
                docs,
                applicationLogs,
                systemEvents,
                databaseSlowQueryLogs
        );
    }

    public Optional<String> extractFinalReport(OverAllState state) {
        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("Extracted planner final report, length: {}", reportText.length());
            return Optional.of(reportText);
        }

        logger.warn("Planner final report was not found in graph state");
        return Optional.empty();
    }

    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("Plans and replans AIOps investigation steps")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("planner_plan")
                .build();
    }

    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("Executes one investigation step and returns evidence")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    private Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        }
        return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
    }

    private String buildPlannerPrompt() {
        return """
                You are planner_agent and replanner for an AIOps investigation.

                Goals:
                - Understand the current incident task and the latest executor_feedback.
                - Decide the next bounded step.
                - Prefer historical memory, Prometheus alerts, internal runbooks, and CLS log search evidence.
                - Never fabricate evidence.

                Tool rules:
                - Historical memory is only background; current tool evidence has priority.
                - For CLS logs, first resolve topic names with GetTopicInfoByName when using MCP tools.
                - application-logs, system-events, and database-slow-query are topic names, not TopicId values.
                - Use ap-guangzhou as the default region.
                - Do not use alarm notice, webhook, shield, or notification-management tools.
                - If a tool fails or returns no useful data, record the failure and move to another evidence source.

                While investigating, output compact JSON:
                {"decision":"EXECUTE","step":"...","toolHint":"...","context":"..."}

                When enough evidence is collected or the investigation cannot continue, output a Markdown report directly.
                The report must include:
                # 告警分析报告
                ## 历史记忆命中
                ## 活跃告警
                ## 证据
                ## 根因分析
                ## 处理建议
                ## 风险与跟进
                """;
    }

    private String buildExecutorPrompt() {
        return """
                You are executor_agent.
                Read the latest planner_plan and execute only the first concrete step.

                Rules:
                - Use only tools required by the step.
                - Prefer historical memory, queryPrometheusAlerts, queryInternalDocs, and log-search tools.
                - Do not use alarm notice, webhook, shield, or notification-management tools.
                - For CLS log tools, resolve topic name to TopicId first when required.
                - Use ap-guangzhou as the default region.
                - Return only real observations from tools. Do not invent logs, metrics, alerts, or timestamps.

                Return compact JSON:
                {
                  "status": "SUCCESS|FAILED",
                  "summary": "...",
                  "evidence": "...",
                  "nextHint": "..."
                }
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                You are ai_ops_supervisor.
                Coordinate planner_agent and executor_agent in a short bounded loop.

                Process:
                1. Ask planner_agent for the next step.
                2. If planner_agent returns decision=EXECUTE, call executor_agent.
                3. Feed executor_feedback back to planner_agent.
                4. Finish once a report is ready, or when tools repeatedly fail.

                Hard limits:
                - Keep the investigation concise.
                - Do not call alarm notice, webhook, shield, or notification-management tools.
                - If evidence is unavailable, finish with a report that clearly states what could not be verified.

                Choose only planner_agent, executor_agent, or FINISH.
                """;
    }
}
