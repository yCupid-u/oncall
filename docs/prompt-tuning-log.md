# Prompt 调优记录

这份文档不是为了证明“写过 prompt”，而是为了证明“知道怎么发现 bad case、定位原因、改策略并复测”。

当前记录分两类：

- `已验证`：代码或测试中已经能看到对应修改。
- `待补样例`：只是调优方向，不能当作已经完成的优化成果。

## 记录模板

### Case ID

- 日期：
- 场景：
- 输入：
- 原输出问题：
- 根因判断：
- 修改策略：
- 修改前 prompt / 工具策略：
- 修改后 prompt / 工具策略：
- 复测结果：
- 是否解决：
- 证据位置：

## 已验证记录

### Case-001：AIOps 报告终态格式不稳定

- 状态：已验证
- 场景：AIOps 分析结束时，最终输出需要给用户阅读，而不是继续暴露中间 JSON。
- 原问题：Planner 的中间决策结构容易影响最终报告可读性。
- 修改策略：在 AIOps 报告生成阶段固定 Markdown 结构，包括告警摘要、执行轨迹、证据、根因、处置建议和历史记忆命中。
- 复测结果：`AiOpsServiceTest` 断言最终报告包含 `Planner-Executor-Replan 执行轨迹`、`历史记忆命中`、告警名和 CLS 查询证据。
- 证据位置：`src/main/java/org/example/service/AiOpsService.java`、`src/test/java/org/example/service/AiOpsServiceTest.java`

### Case-002：MCP SSE 链路不稳定，切换到本地 stdio

- 状态：已验证到配置层
- 场景：腾讯云托管 SSE endpoint 出现 404，导致 MCP 工具调用失败。
- 原问题：外部 SSE 会话失效时，Agent 链路无法稳定拿到 CLS 工具。
- 修改策略：改为本地 `npx cls-mcp-server@latest` stdio 方式，并增加 wrapper 过滤启动日志，避免污染 MCP JSON-RPC stdout。
- 复测结果：本地 `cmd /c npx -y cls-mcp-server@latest --help` 能启动 stdio；项目配置已切到 stdio。
- 证据位置：`scripts/cls-mcp-stdio-wrapper.js`、`src/main/resources/application.yml`

### Case-003：缺少长期记忆，重复故障不能复用经验

- 状态：已验证
- 场景：同类告警再次出现时，希望 Agent 能先读历史经验。
- 原问题：原链路只看当前告警、知识库和日志，不会复用历史处置结果。
- 修改策略：增加 JSON 记忆库；AIOps 分析前检索历史记忆，分析后写入故障摘要、根因、动作和证据。
- 复测结果：`MemoryServiceTest`、`JsonMemoryStoreTest`、`AiOpsServiceTest` 通过。
- 证据位置：`src/main/java/org/example/memory`、`src/main/java/org/example/controller/MemoryController.java`

## 待补样例

下面这些是后续应补的 bad case，不应在面试中说成已经完成。

### 1. 只总结告警，不主动查日志

- 典型现象：模型读取告警后直接给结论，没有调用日志工具。
- 可能原因：prompt 中“先取证再下结论”的约束不够强。
- 建议修改：要求报告中的根因必须引用至少一条日志、指标或知识库证据。

### 2. 工具调用顺序混乱

- 典型现象：先猜根因，再回头查日志。
- 可能原因：Planner 缺少固定排查优先级。
- 建议修改：固定“告警 -> 指标/日志 -> 文档 -> 结论”的执行顺序。

### 3. 工具空结果后编造

- 典型现象：日志工具没有返回证据，但报告仍给出确定根因。
- 可能原因：缺少“证据不足”输出约束。
- 建议修改：工具连续失败或空结果时，必须输出“不足以确认根因”。

### 4. region / topic 参数不规范

- 典型现象：region 写成中文地名，topic 不在配置白名单。
- 可能原因：tool schema 和 prompt 没有同时约束。
- 建议修改：工具层强校验，prompt 层明确可选 region / topic。

### 5. 报告只有现象，没有处置动作

- 典型现象：报告有日志证据和根因，但没有短期止血和长期修复建议。
- 可能原因：报告模板缺少动作字段。
- 建议修改：固定输出“立即处理、验证方式、长期修复、风险说明”。

## 下一步执行要求

每次补 bad case 必须保留：

1. 输入。
2. 原输出。
3. 问题判断。
4. 修改策略。
5. 新输出。
6. 是否改善。

没有 before / after 的内容，只能算调优计划，不能算调优证据。
