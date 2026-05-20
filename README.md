# OnCallPilot

> 智能值班排障助手，基于 Spring Boot + Spring AI Alibaba 演示 Agent 编排、RAG 知识库、工具调用、日志检索和 AIOps 排障流程。

## 📖 项目简介

OnCallPilot 是一个面向 OnCall 排障场景的 Agent 工程项目。它把告警读取、知识库检索、日志查询、历史记忆、根因分析和处置建议串成一条可解释链路，用于展示 Agent + RAG + Tool Use 在智能运维场景中的完整落地方式。

项目提供可本地复现的运行链路、测试用例、评测入口和腾讯云 CLS 接入配置，适合作为 Agent 工程作品集展示。

项目包含三大核心模块：

### 1. RAG 智能问答
集成 Milvus 向量数据库和阿里云 DashScope，提供基于检索增强生成的智能问答能力，支持多轮对话和流式输出。

### 2. AIOps 智能运维
基于 AI Agent 的自动化运维系统，采用 Planner-Executor-Replanner 架构，实现告警分析、日志查询、智能诊断和报告生成。

### 3. Agent 记忆与评测
提供 JSON 长期记忆、Session 短期记忆窗口、RAG 召回评测和项目说明文档，方便展示 Agent 记忆、检索评测和工具链路设计。

## 🚀 核心特性

- ✅ **RAG 问答**: 向量检索 + 多轮对话 + 流式输出
- ✅ **AIOps 运维**: 智能诊断 + 多 Agent 协作 + 自动报告
- ✅ **工具集成**: 文档检索、告警查询、CLS 日志分析、时间工具
- ✅ **会话管理**: Session 隔离、窗口裁剪、Token 估算
- ✅ **长期记忆**: JSON 存储故障经验，分析前检索，分析后写入
- ✅ **RAG 评测**: 支持 `hit@1`、`hit@k`、`MRR` 样例集评测
- ✅ **Web 界面**: 提供测试界面和 RESTful API

## 📁 投递材料

如果这个仓库用于 Agent 岗位投递，建议优先阅读以下文档：

- `docs/agent-portfolio.md`：项目作品集说明
- `docs/job-fit-analysis.md`：与岗位要求的逐项匹配分析
- `docs/prompt-tuning-log.md`：Prompt 调优记录模板与待补案例
- `docs/project-followup-roadmap.md`：后续补齐路线图
- `docs/resume-verification-report.md`：简历表述与项目证据映射


## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI | - | AI Agent 框架 |
| DashScope | 2.17.0 | 阿里云 AI 服务 |
| Milvus | 2.6.10 | 向量数据库 |
| Tencent CLS SDK | 3.1.1423 | 腾讯云日志服务测试主题查询 |

## 📦 核心模块

```
OnCallPilot/
├── src/main/java/org/example/
│   ├── controller/
│   │   └── ChatController.java        # 统一接口控制器 ⭐
│   ├── service/
│   │   ├── ChatService.java           # 对话服务 ⭐
│   │   ├── AiOpsService.java          # AIOps 服务 ⭐
│   │   ├── MemoryService.java         # JSON 长期记忆
│   │   ├── SessionMemory.java         # Session 短期记忆
│   │   ├── RagEvalRunner.java         # RAG 评测入口
│   │   ├── RagService.java            # RAG 服务
│   │   └── Vector*.java               # 向量服务
│   ├── agent/tool/                    # Agent 工具集
│   │   ├── DateTimeTools.java         # 时间工具
│   │   ├── InternalDocsTools.java     # 文档检索
│   │   ├── QueryMetricsTools.java     # 告警查询
│   │   └── QueryLogsTools.java        # 日志查询
│   └── config/                        # 配置类
├── docs/                              # 作品集、岗位匹配、调优记录
├── eval/                              # RAG 评测样例集
├── scripts/                           # CLS 种子数据和验证脚本
├── src/main/resources/
│   ├── static/                        # Web 界面
│   └── application.yml                # 应用配置
└── aiops-docs/                        # 运维文档库
```

## 🧰 Agent 工具调用

项目通过 Spring AI `@Tool` 暴露本地工具，并预留 MCP 外部工具接入：

| 工具名 | 来源 | 作用 |
|------|------|------|
| `getCurrentDateTime` | `DateTimeTools` | 获取当前时间，用于对话和报告时间上下文 |
| `queryInternalDocs` | `InternalDocsTools` | 基于 Milvus + Rerank 检索内部知识库，返回结构化文档片段 |
| `queryPrometheusAlerts` | `QueryMetricsTools` | 查询当前 Prometheus 活动告警，支持本地示例数据和真实 Prometheus API |
| `queryPrometheusAlertByName` | `QueryMetricsTools` | 按告警名过滤活动告警，例如 `HighCPUUsage`、`SlowResponse` |
| `getAvailableLogTopics` | `QueryLogsTools` | 查询可用日志主题和示例查询语句 |
| `queryLogs` | `QueryLogsTools` | 查询系统指标、应用日志、慢查询和系统事件日志，支持参数校验、本地示例数据和腾讯云 CLS 测试主题 |

默认公开演示配置关闭 MCP：

```yaml
spring.ai.mcp.client.enabled: false
```

如需接入云日志、CMDB、工单系统等外部能力，可以通过 MCP 服务暴露工具，再由 `ToolCallbackProvider` 注入给 Agent。当前项目已预留腾讯云 CLS 的本地 stdio MCP / Java SDK 接入方式，可用于展示云日志检索工具链路。

## 🧠 记忆系统

项目包含两层记忆：

| 类型 | 实现 | 作用 |
| --- | --- | --- |
| 短期记忆 | `SessionMemory` | 按 `sessionId` 隔离多轮对话，保留固定窗口，裁剪旧消息并统计 Token 节省比例 |
| 长期记忆 | `JsonMemoryStore` | 将 AIOps 故障摘要、根因、动作建议和证据写入 JSON 文件，后续分析前可检索复用 |

默认长期记忆路径：

```bash
data/agent-memory/memory-store.json
```

可用环境变量覆盖：

```bash
AGENT_MEMORY_PATH=custom/path/memory-store.json
```

记忆 API：

- `GET /api/memory` - 查看全部记忆
- `GET /api/memory/search` - 按服务、告警、关键词检索
- `POST /api/memory` - 写入一条故障记忆
- `DELETE /api/memory` - 清空记忆库

## 📊 RAG 评测

项目提供本地样例集评测入口，用于验证检索链路是否能召回期望 runbook 或关键内容。

配置项：

```yaml
rag:
  eval:
    enabled: false
    dataset: eval/rag-eval.jsonl
    output: target/rag-eval-report.json
    top-k: 3
    fail-fast: false
```

指标：

- `hit@1`
- `hit@k`
- `MRR`

说明：当前评测聚焦 RAG 召回质量，用于观察不同分块、TopK 和 rerank 策略对知识命中的影响。

## 📡 核心接口

### 1. 智能问答接口

**流式对话（推荐）**
```bash
POST /api/chat_stream
Content-Type: application/json

{
  "Id": "session-123",
  "Question": "什么是向量数据库？"
}
```
支持 SSE 流式输出、自动工具调用、多轮对话。

**普通对话**
```bash
POST /api/chat
Content-Type: application/json

{
  "Id": "session-123",
  "Question": "什么是向量数据库？"
}
```
一次性返回完整结果，支持工具调用和多轮对话。

### 2. AIOps 智能运维接口

```bash
POST /api/ai_ops
```
自动执行告警分析流程，生成运维报告（SSE 流式输出）。

### 3. 会话管理

- `POST /api/chat/clear` - 清空会话历史
- `GET /api/chat/session/{sessionId}` - 获取会话信息

### 4. 记忆管理

- `GET /api/memory` - 查看全部长期记忆
- `GET /api/memory/search` - 检索长期记忆
- `POST /api/memory` - 写入长期记忆
- `DELETE /api/memory` - 清空长期记忆

### 5. 工具状态

- `GET /api/tools` - 查看本地工具和 MCP 工具注入状态

### 6. 文件管理

- `POST /api/upload` - 上传文件并自动向量化
- `GET /milvus/health` - Milvus 健康检查


## ⚙️ 核心配置

### application.yml

```yaml
server:
  port: 9999

# Milvus 向量数据库
milvus:
  host: localhost
  port: 19530

# 阿里云 DashScope
spring:
  ai:
    dashscope:
      api-key: "${DASHSCOPE_API_KEY}" // 环境变量

# RAG 配置
rag:
  top-k: 3
  model: "qwen3-max"
  eval:
    enabled: false
    dataset: eval/rag-eval.jsonl
    output: target/rag-eval-report.json

# 文档分片
document:
  chunk:
    max-size: 800
    overlap: 100

# 长期记忆
agent:
  memory:
    path: ${AGENT_MEMORY_PATH:data/agent-memory/memory-store.json}
```

### 环境变量

```bash
export DASHSCOPE_API_KEY=your-api-key
```


## 🔐 配置说明

项目不提交任何真实 API Key。首次运行前请复制环境变量模板并填入自己的配置：

```bash
cp .env.example .env
```

至少需要配置：

```bash
DASHSCOPE_API_KEY=your-dashscope-api-key
```

如需验证腾讯云 CLS 测试主题查询，可额外配置：

```bash
TENCENTCLOUD_SECRET_ID=your-tencent-secret-id
TENCENTCLOUD_SECRET_KEY=your-tencent-secret-key
TENCENT_CLS_DEFAULT_REGION=ap-guangzhou
TENCENT_CLS_TOPIC_APPLICATION_LOGS=your-topic-id
TENCENT_CLS_TOPIC_SYSTEM_EVENTS=your-topic-id
TENCENT_CLS_TOPIC_DATABASE_SLOW_QUERY=your-topic-id
```

MCP、Prometheus、CLS 日志查询默认关闭或使用本地示例配置，可按需在 `.env` 或环境变量中开启。

## 🚀 快速开始

### 1. 环境准备

```bash
# 设置 API Key，或使用 .env 文件
export DASHSCOPE_API_KEY=your-api-key
```

### 2. 启动应用

方法一： 手动启动
```bash
1.先启动向量数据库
docker compose up -d -f vector-database.yml

2.启动服务
mvn clean install
mvn spring-boot:run
```

方法二：一键启动
```bash
make init  # 会自动启动向量数据库并上传运维文档到向量库
```


### 3. 使用示例

**Web 界面**
```
http://localhost:9999
```

**命令行**
```bash
# 上传文档
curl -X POST http://localhost:9999/api/upload \
  -F "file=@document.txt"

# 智能问答
curl -X POST http://localhost:9999/api/chat \
  -H "Content-Type: application/json" \
  -d '{"Id":"test","Question":"什么是向量数据库？"}'

# 健康检查
curl http://localhost:9999/milvus/health
```

## ✅ 本地验证

常用验证命令：

```bash
# 编译
mvn -q -DskipTests compile

# 核心测试
mvn -q "-Dtest=DocumentChunkServiceTest,SessionMemoryTest,JsonMemoryStoreTest,MemoryServiceTest,AiOpsServiceTest" test

# 简历证据检查
powershell -ExecutionPolicy Bypass -File scripts/verify-resume-claims.ps1 -SkipMaven
```

## 🧾 项目展示口径

这个仓库可以直接作为 Agent 工程作品集使用，重点展示以下能力：

- 完整跑通 `告警读取 -> RAG 检索 -> 日志查询 -> 根因分析 -> 建议生成 -> 记忆写入` 的 AIOps Agent 链路。
- 基于 Spring AI Alibaba 拆分 Planner / Executor / Supervisor，体现多 Agent 编排和工具调用控制。
- 基于 Milvus 构建运维知识库，支持文档切割、向量召回、TopK 检索、rerank 和 RAG 评测。
- 接入腾讯云 CLS / 本地日志样例，展示 MCP 与云日志工具链路的集成方式。
- 实现 Session 短期记忆和 JSON 长期记忆，展示上下文窗口裁剪、历史经验复用和分析后写入。

面试介绍时建议突出“可复现链路 + 可验证代码 + 可继续扩展的工程设计”，而不是单纯描述概念。


**版本**: v1.0.0  
**作者**: yCupid  
**许可证**: MIT



