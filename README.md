# OnCallPilot

> 智能值班排障助手，基于 Spring Boot + Spring AI Alibaba 演示 RAG 知识库、多轮对话、工具调用和 AIOps 排障流程。

## 📖 项目简介

OnCallPilot 是一个智能值班排障助手演示项目，包含两大核心模块：

### 1. RAG 智能问答
集成 Milvus 向量数据库和阿里云 DashScope，提供基于检索增强生成的智能问答能力，支持多轮对话和流式输出。

### 2. AIOps 智能运维
基于 AI Agent 的自动化运维系统，采用 Planner-Executor-Replanner 架构，实现告警分析、日志查询、智能诊断和报告生成。

## 🚀 核心特性

- ✅ **RAG 问答**: 向量检索 + 多轮对话 + 流式输出
- ✅ **AIOps 运维**: 智能诊断 + 多 Agent 协作 + 自动报告
- ✅ **工具集成**: 文档检索、告警查询、日志分析、时间工具
- ✅ **会话管理**: 上下文维护、历史管理、自动清理
- ✅ **Web 界面**: 提供测试界面和 RESTful API


## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI | - | AI Agent 框架 |
| DashScope | 2.17.0 | 阿里云 AI 服务 |
| Milvus | 2.6.10 | 向量数据库 |

## 📦 核心模块

```
OnCallPilot/
├── src/main/java/org/example/
│   ├── controller/
│   │   └── ChatController.java        # 统一接口控制器 ⭐
│   ├── service/
│   │   ├── ChatService.java           # 对话服务 ⭐
│   │   ├── AiOpsService.java          # AIOps 服务 ⭐
│   │   ├── RagService.java            # RAG 服务
│   │   └── Vector*.java               # 向量服务
│   ├── agent/tool/                    # Agent 工具集
│   │   ├── DateTimeTools.java         # 时间工具
│   │   ├── InternalDocsTools.java     # 文档检索
│   │   ├── QueryMetricsTools.java     # 告警查询
│   │   └── QueryLogsTools.java        # 日志查询
│   └── config/                        # 配置类
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
| `queryLogs` | `QueryLogsTools` | 查询系统指标、应用日志、慢查询和系统事件日志，支持参数校验和本地示例数据 |

默认公开演示配置关闭 MCP：

```yaml
spring.ai.mcp.client.enabled: false
```

如需接入真实云日志、CMDB、工单系统等外部能力，可以通过 MCP 服务暴露工具，再由 `ToolCallbackProvider` 注入给 Agent。

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

### 4. 文件管理

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

# 文档分片
document:
  chunk:
    max-size: 800
    overlap: 100
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


**版本**: v1.0.0  
**作者**: yCupid  
**许可证**: MIT



