# OnCallPilot 与岗位要求匹配分析

本文按岗位截图中的要求做如实映射，分为三类：

- `已体现`：代码、测试或文档中有明确证据。
- `部分体现`：有基础实现，但证据链还不够完整。
- `未体现`：当前项目中基本看不到，需要后续补材料或补功能。

## 岗位职责匹配

| 岗位描述 | 当前体现 | 证据 | 结论 |
| --- | --- | --- | --- |
| 自己动手做 Agent，搭路径编排、调 Prompt、做工具策略、跑评测 | 有 AIOps Agent、工具调用、RAG、评测入口 | `AiOpsService`、`ChatService`、`RagEvalRunner` | `已体现` |
| 持续优化 Agent 准确率，整理 bad case，维护评测集，做 A/B 对比 | 有评测入口和调优记录模板，但 A/B 和 bad case 还不完整 | `eval/rag-eval.jsonl`、`docs/prompt-tuning-log.md` | `部分体现` |
| 跟进研发、设计、算法团队需求落地 | 当前是个人项目，缺跨团队协作证据 | 无 PR 评审、需求单或协作记录 | `未体现` |
| 半年内负责小模块，逐步独当一面 | 能体现独立搭建模块，但不能证明线上长期维护 | 当前仓库代码和测试 | `部分体现` |

## 任职要求匹配

| 任职要求 | 当前体现 | 证据 | 结论 |
| --- | --- | --- | --- |
| 必须提供 Agent 作品集 | 已整理为项目说明，但还缺截图/录屏 | `docs/agent-portfolio.md` | `部分体现` |
| 有亲手调 Agent 的实操经验，并能讲清为什么这样设计 | 有工程实现，调优证据还需要继续补 | `AiOpsService`、`docs/prompt-tuning-log.md` | `部分体现` |
| 会至少一门编程语言，能完成基础大模型 API 调用 | Java + Spring Boot + Spring AI Alibaba | 项目源码 | `已体现` |
| 对 Prompt、Tool Use、RAG、Context Window、MCP 有清晰认知 | 项目覆盖这些概念，但需要面试表达配合 | 源码与文档 | `已体现` |
| 英文阅读能力 OK，能读官方文档 | 项目无法直接证明 | 可在面试中说明阅读 Spring AI / MCP / 云厂商文档经历 | `未体现` |

## 作品集要求匹配

| 作品集示例 | 当前情况 | 结论 |
| --- | --- | --- |
| 扣子 / Dify / Coze 工作流或 Bot | 当前没有平台版 workflow | `未体现` |
| 用 Claude / GPT API 做 Agent Demo 或自动化脚本 | 当前项目符合“Agent Demo”方向，但使用的是 DashScope / Spring AI Alibaba | `已体现` |
| 公开发布的 GPTs / Claude Project | 没有公开发布版本 | `未体现` |
| GitHub 上跑通并改过的 Agent 开源项目 | 当前是自建 Agent 项目，不是二次贡献开源项目 | `部分体现` |
| 系统 Prompt 调优记录或 Bad case 分析 | 已有模板和少量记录，仍需补真实 before / after | `部分体现` |

## 可以放心写进简历的点

- 基于 Spring AI Alibaba 实现 AIOps Agent 原型。
- 拆分 Planner / Executor / Supervisor 的排障链路。
- 接入 RAG 知识库和日志查询工具。
- 实现本地 JSON 长期记忆第一版。
- 建立 RAG 样例评测入口，支持 hit@1、hit@k、MRR 输出。

## 需要谨慎表达的点

- 不建议直接写“生产准确率 85%+”。可以写“在本地样例集上用 hit@k 做召回评测，目标线为 85%+”。
- 不建议直接写“排障效率从小时级压缩到分钟级”。可以写“本地 demo 将排障步骤自动串联，具备分钟级生成排障报告的原型能力”。
- 不建议写“真实企业数据”。可以写“使用腾讯云 CLS 测试主题和样例日志验证链路”。
- 不建议写“完整 Prompt A/B 优化”。当前只能写“已建立 Prompt 调优记录模板，正在补充 bad case 和 A/B 对比”。

## 后续最该补的证据

1. 补 5 条 bad case，每条都有输入、旧输出、问题、修改策略、新输出。
2. 补 2 组 A/B 对比：planner vs 单 Agent、rerank vs 无 rerank。
3. 补一次完整的 AIOps 截图或录屏。
4. 把 RAG 评测报告保存为可复现文件，不依赖口头描述。
5. 如有时间，再做一个 Dify / Coze 轻量 workflow。
