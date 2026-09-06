# 代码审查清单（多 Agent 互审）

> 适用：agent 的改动由另一个 agent 审查；或提交前自审。
> 原则：审查者**只读代码与测试**，用 grep / git log 证实结论（verify-don't-assume），不凭印象。

## 1. 范围与原子性

- [ ] 一个 commit 只做一件事，可独立 revert（AGENTS.md §5.2 三问）
- [ ] 无调试残留（`git grep -nP 'System\.out|console\.log|print\(|println'` 自查）
- [ ] 生成物不入库（`build/`、`.gradle/`、`node_modules/`、`*.db`、`.next/`、`dist/`）
- [ ] subject ≤72 字符、scope 在白名单（AGENTS.md §5.1）、commitlint 过

## 2. 架构铁律（AGENTS.md §3）

- [ ] 未跨模块直接访问他模块 repository（只走 `api/` 接口 / 领域事件）
- [ ] blocks 用 JSONB，区块树 `parent_id + children` 数组维护，无全表重排
- [ ] AI 转换走异步任务 + 规则优先；LLM 输出带 evidence + confidence；低置信进校对门
- [ ] 服务端未解析 Yjs 内部结构（只透传 + 快照）
- [ ] 数据访问全部过 `workspace_id` 校验（无越权）
- [ ] 未引入 Maven / husky / 硬编码 LLM 供应商 / JDK 8 语法

## 3. Java / Spring 规范

- [ ] 分层 controller/service/repository，controller 薄，DTO 与实体分离
- [ ] 异常统一由 GlobalExceptionHandler 处理，无散落 try-catch 吞异常
- [ ] SQL 参数化 / JPA 派生查询，无字符串拼接 SQL
- [ ] 日志用 SLF4J；错误路径有日志，热路径不打印
- [ ] 环境变量读配置，密钥不入库

## 4. 并发与异步

- [ ] 异步任务幂等（同一 jobId 重试不重复建卡片）
- [ ] 共享状态有锁 / 事务边界清晰，无裸并发写
- [ ] 长生命周期协程/线程用独立生命周期管理，不继承请求级上下文

## 5. 前端 / 多端视角

- [ ] packages/core 未依赖 Next.js 专有 API
- [ ] API 调用只走 api-client，无组件内裸 fetch
- [ ] 类型来自生成契约（strict，无 `any`）
- [ ] 桌面/移动端未绕过后端 API 直连数据库

## 6. 测试与验证

- [ ] 新增业务逻辑有单测；关键链路（认证/块 CRUD/看板拖拽/转换主链路）有集成测试
- [ ] LLM 相关变更跑过 Golden 回归（准确率 ≥ 阈值：title 95% / assignee 90% / due_date 90%）
- [ ] 验证矩阵（milestone-flow.md）全绿；`bash .agents/tools/check.sh` 通过

## 7. 文档与交接

- [ ] 改动 API/数据模型/ADR 时，docs/ 与 AGENTS.md 已同步（否则打回）
- [ ] 完成报告按《类Notion平台_AI-Agent开发规范.md》§5 模板提交
- [ ] 当日日志已更新（.agents/history/）
