# JVM Full GC 与 OOM 告警处理方案

## 告警名称

- **告警名**: `JvmFullGC` / `OutOfMemoryError`
- **告警级别**: 严重
- **触发条件**: Full GC 频繁、GC 后内存无法回落，或应用日志出现 `OutOfMemoryError`。

## 问题描述

JVM Full GC 和 OOM 通常会造成接口抖动、请求超时、实例重启或服务不可用。排障时要区分内存泄漏、流量突增、大对象分配、缓存无限增长和 JVM 参数配置不合理。

## 检索关键词

`Full GC`、`OutOfMemoryError`、`GC overhead limit exceeded`、`heap dump`、`memory leak`、`Metaspace`、`direct buffer memory`

## 排查步骤

### 步骤 1：查询应用错误日志

- 工具：`queryLogs`
- 日志主题：`application-logs`
- 查询条件示例：`OutOfMemoryError OR "Full GC" OR "GC overhead"`
- 重点字段：`service_name`、`instance_id`、`trace_id`、`exception`、`heap_used`

### 步骤 2：查询系统资源日志

- 工具：`queryLogs`
- 日志主题：`system-events`
- 查询条件示例：`memory_usage > 85 OR oom_kill:true`
- 目标：确认是否存在容器 OOM Kill、内存持续上涨或 swap 异常。

### 步骤 3：判断内存类型

- 堆内存：关注 `java.lang.OutOfMemoryError: Java heap space`。
- 元空间：关注 `Metaspace`。
- 直接内存：关注 `Direct buffer memory`。
- GC 开销：关注 `GC overhead limit exceeded`。

### 步骤 4：保留现场

如果实例还未重启，应优先导出 heap dump、线程栈和 GC 日志，再做重启或扩容。

## 常见原因

### 原因 1：内存泄漏

特征：

- Full GC 后内存无法明显下降。
- 内存曲线随运行时间持续上涨。
- dump 中某类对象数量异常。

处理：

1. 导出 heap dump。
2. 使用 MAT 或类似工具分析 dominator tree。
3. 找到持有大量对象的集合、缓存或静态引用。
4. 修复泄漏代码并发布。

### 原因 2：缓存无限增长

特征：

- 缓存命中率不一定高，但缓存对象持续增长。
- 缓存没有 TTL、容量上限或淘汰策略。
- 请求高峰后内存无法回落。

处理：

1. 临时降低缓存容量或清理热点缓存。
2. 增加 TTL、最大容量和 LRU/LFU 淘汰策略。
3. 对大对象缓存做压缩或拆分。

### 原因 3：流量突增导致对象分配过快

特征：

- 内存和 QPS 同步上升。
- GC 后内存能回落，但 GC 频率过高。
- 接口响应时间随 GC 抖动。

处理：

1. 扩容应用实例。
2. 对非核心接口限流。
3. 减少请求链路中的临时对象创建。

## 应急处理

1. 保留 heap dump、GC 日志和线程栈。
2. 扩容或重启异常实例，先恢复服务。
3. 如果由新版本引入，回滚到稳定版本。
4. 对高风险接口临时限流或降级。

## 验证方式

1. Full GC 频率下降。
2. GC 后堆内存能回落。
3. OOM 或容器重启不再出现。
4. P99 响应时间恢复稳定。

