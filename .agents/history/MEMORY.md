# TransNote 项目长期笔记

> 这是 `~/code/transnote` 的项目级长期记忆。当前阶段：**Phase 0 准备**。
> 完整权威定义在 `AGENTS.md` + `docs/类Notion平台_MVP开发交接文档.md`。

## 关键 ADR（架构决策记录）

> 完整决策文档后续放 `docs/adr/`（每份含背景/决策/备选/落地/验证）；本表先做索引。
> 来源：交接文档 §3 决策表，Phase 0 已拍板。

| ADR | 决策（一句话） |
|---|---|
| ADR-1 | 模块化单体（Spring Modulith），不直接上微服务；热点模块可后拆 |
| ADR-2 | 实时协作选 Yjs（CRDT），不造 OT |
| ADR-3 | 服务端不解析 Yjs 内部结构，只透传 + 存二进制快照（BYTEA） |
| ADR-4 | AI 转换走异步任务（HTTP 返回 jobId），LLM 耗时不阻塞链路 |
| ADR-5 | 规则优先（正则），LLM 只做语义抽取；输出带 evidence + confidence；低置信进人工校对 |
| ADR-6 | 消息用 Redis Stream 起步，量起来换 Kafka（接口抽象隔离） |
| ADR-7 | Web/桌面共享 core 包（bun workspaces monorepo），移动端独立 UI 但共享 API 契约 |
| ADR-8 | 构建工具用 **Gradle**（不用 Maven）；前端包管理用 **bun**（不用 pnpm/npm） |
| ADR-9 | Git 钩子用 **lefthook**（不用 husky，与 lanchat 一致）；提交校验 @commitlint/cli + config-conventional |

## 里程碑状态

| Milestone | 状态 | 说明 |
|---|---|---|
| Phase 0 准备（.agents + AGENTS.md + CLAUDE.md） | 🚧 进行中 | 2026-09-06 落盘，待用户确认项目名与后续步骤 |
| Phase 1 MVP（T1–T12） | ⬜ 未开始 | 任务清单见 AGENTS.md §1.1 |
| Phase 2 协作/桌面/搜索 | ⬜ | |
| Phase 3 移动端/打磨 | ⬜ | |

## 技术栈快照（用户已拍板，勿擅改）

- 后端：**Java 25（LTS）+ Spring Boot 4.1.1** + Spring Modulith，构建 **Gradle**（Boot 4：Web 用 `spring-boot-starter-webmvc`）
- 前端：Next.js 14/15 + React + TS；桌面 Tauri 2；移动 Flutter 3（Phase 3，**Windows 侧开发**——WSL 不适合 Flutter 工具链/设备调试，见 AGENTS.md §7）
- 包管理：**bun workspaces**（`apps/*`、`packages/*`）；Git 钩子 **lefthook**；提交校验 commitlint
- 存储：PostgreSQL 16（JSONB）+ Redis 7 + MinIO；搜索 ES 8（后置）
- AI：OpenAI 兼容多 provider（LlmProvider 抽象）+ 人工校对门 + Golden 回归

## 持续关注（踩坑沉淀，开发后持续追加）

- （空，Phase 1 开始后按 milestone-flow.md 格式追加）

## 待办（跨会话）

- [x] 项目定名 **TransNote**（仓库 `~/code/transnote`，Java 包 `com.transnote.*`）
- [x] commitlint + lefthook 落地（2026-09-06 实测：@commitlint/cli 21.2.2 / lefthook 2.1.12；坏提交 feat(badscope) 已被 commit-msg 拦截；pre-commit 密钥扫描）
- [x] 集成 semantic-release 并完成首次发布 v0.0.1（2026-09-06：25.0.9 + changelog 7.0.0 + git 11.0.1 + conventionalcommits 9.3.1 显式安装；`bun run release` 自动生成 CHANGELOG + `chore(release)` 提交 + tag；repositoryUrl 用 `file://` 本地占位，接 GitHub 时替换）
- [ ] T1（进行中）：已建 server Gradle wrapper + Spring Boot 4.1.1 基础工程 + spotless；剩余 bun workspaces 依赖声明（T1.2）与 infra/docker-compose.yml（T1.3）
- [ ] 设 JAVA_HOME / sdkman 默认指向 GraalVM 25（Java 25 + Spring Boot 4.1.1 已定版）
- [ ] 接入 commitlint + lefthook（lefthook.yml + commitlint.config.js + hook install）
- [ ] git init + 首次提交（chore(repo): 初始化 .agents 与工程约定）
