# OnCallPilot 与岗位要求匹配分析

本文按岗位截图中的要求做能力映射，重点说明项目已经体现的 Agent 工程能力，以及后续可以继续增强的方向。

## 岗位职责匹配

| 岗位描述 | 当前体现 | 证据 | 结论 |
| --- | --- | --- | --- |
| 自己动手做 Agent，搭路径编排、调 Prompt、做工具策略、跑评测 | 已实现 AIOps Agent、工具调用、RAG 和评测入口 | `AiOpsService`、`ChatService`、`RagEvalRunner` | 已体现 |
| 持续优化 Agent 准确率，整理 bad case，维护评测集，做 A/B 对比 | 已有 RAG 评测入口和 Prompt 调优记录文档，后续可继续补充更多对比样例 | `eval/rag-eval.jsonl`、`docs/prompt-tuning-log.md` | 部分体现 |
| 跟进研发、设计、算法团队需求落地 | 项目体现了从需求拆解到工程实现的完整闭环 | 源码、测试、README、作品集文档 | 部分体现 |
| 半年内负责小模块，逐步独当一面 | 项目覆盖 Agent、RAG、工具、记忆和评测多个模块 | 仓库代码与测试 | 已体现 |

## 任职要求匹配

| 任职要求 | 当前体现 | 证据 | 结论 |
| --- | --- | --- | --- |
| 必须提供 Agent 作品集 | 已整理 README 和作品集说明 | `README.md`、`docs/agent-portfolio.md` | 已体现 |
| 有亲手调 Agent 的实操经验，并能讲清为什么这样设计 | 项目体现 Planner-Executor、工具约束、RAG 召回和记忆机制设计 | `AiOpsService`、`QueryLogsTools`、`docs/prompt-tuning-log.md` | 已体现 |
| 会至少一门编程语言，能完成大模型 API 调用 | Java + Spring Boot + Spring AI Alibaba | 项目源码 | 已体现 |
| 对 Prompt、Tool Use、RAG、Context Window、MCP 有清晰认知 | 项目覆盖这些概念，并有对应代码落点 | 源码与文档 | 已体现 |
| 英文阅读能力 OK，能读官方文档 | 技术栈涉及 Spring AI、MCP、云厂商 SDK | 项目依赖与配置 | 可在面试中说明 |

## 作品集要求匹配

| 作品集示例 | 当前情况 | 结论 |
| --- | --- | --- |
| Dify / Coze / 扣子工作流或 Bot | 当前是 Java 工程版 Agent，后续可补一个轻量平台版 workflow | 可增强 |
| 用 Claude / GPT API 做 Agent Demo 或自动化脚本 | 当前项目符合 Agent Demo 方向，使用 DashScope / Spring AI Alibaba | 已体现 |
| 一个公开发布的 GPTs / Claude Project | 当前主线是开源工程仓库 | 可增强 |
| GitHub 上跑通并改过的 Agent 开源项目 | 当前是自建 Agent 项目，能体现端到端工程能力 | 已体现 |
| 系统 Prompt 调优记录或 Bad case 分析 | 已有记录文档，可继续补 before / after 样例 | 部分体现 |

## 可写进简历的亮点

- 基于 Spring AI Alibaba 实现 OnCallPilot AIOps Agent，完成告警读取、RAG 检索、日志查询、根因分析和建议生成链路。
- 拆分 Planner / Executor / Supervisor，实现多 Agent 协作和可解释排障流程。
- 接入 Milvus 运维知识库，支持文档分块、向量召回、TopK 检索、rerank 和 RAG 指标评测。
- 接入腾讯云 CLS / 本地日志工具，展示 Agent Tool Use 和 MCP 工具链路设计。
- 实现 Session 短期记忆与 JSON 长期记忆，支持历史故障经验检索和分析后写入。

## 后续增强优先级

1. 补充 5 条 bad case，每条包含输入、旧输出、问题、修改策略、新输出和证据位置。
2. 增加 2 组 A/B 对比：Planner vs 单 Agent、rerank vs 无 rerank。
3. 补充一次完整 AIOps 运行截图或录屏。
4. 保存 RAG 评测报告为可复现文件。
5. 如有时间，再做一个 Dify / Coze / 扣子轻量 workflow。
