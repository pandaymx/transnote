# AGENTS.md

> 本文件是给 AI 编码助手（Claude / CodeBuddy / Cursor / Doubao 等）的工程约定。
> **动手改代码前先读完。** 与口语指令冲突时，以本文件为准——除非用户明确说「这次按我说的改」。
> Claude Code 场景：`CLAUDE.md` 只是入口，默认 `@AGENTS.md` 引用本文件，**以本文件为准**。

---

## 1. 项目是什么

**TransNote** —— 类 Notion 的块级协作平台：文档、待办看板，核心差异化是 **AI 驱动的 Word ↔ 待办看板双向转换**（带逐条追溯）。

**MVP 判据（一句话）**：在 Web 上能建文档、写块、建看板并拖卡片；上传一个 .docx 能自动变成待办看板（负责人/截止/优先级），看板能一键导出成排版良好的 .docx，且每条任务可跳回原文段落。

**当前阶段**：Phase 1 MVP——T1 后端基础工程已完成（Spring Boot 4.1.1 + spotless + infra compose，均过钩子提交）；Web 层定 **Servlet**（ADR-10）。当前任务：T2 工作区模块（认证后置，ADR-11）。任务进度见 §1.1。

### 1.1 任务进度（权威拆分在 `docs/类Notion平台_MVP开发交接文档.md` §11）

| # | 主题 | 交付物 | 状态 |
|---|---|---|---|
| T1 | Monorepo 脚手架 + Docker Compose 环境 | 根 bun workspaces + server Gradle 骨架 + infra/compose | ⬜ |
| T2 | 工作区表 + CRUD（**认证/RBAC 后置**，公司内网暂免登录） | server/modules/identity（workspace 部分） | ✅ |
| T2.1 | 用户/认证/JWT/成员 RBAC（后置） | server/modules/identity | ⬜ |
| T3 | 文档/块 CRUD + 版本号 | server/modules/document + blocks 表 | ✅ |
| T4 | 块编辑器（Web） | packages/core + apps/web（T3 API 已就绪） | ⬜ |
| T5 | 看板 CRUD + 拖拽 | server/modules/board（apps/web 待 T4 前端） | ✅ |
| T6 | Word 解析 + 分块 | server/modules/conversion（POI 5.5.1 + DocElement 树） | ✅ |
| T7 | LLM Provider + 结构化抽取 | LlmProvider 抽象 + JSON Schema 抽取（规则回退基线可用） | ✅ |
| T8 | Word→看板 全链路 + 人工校对 | job/items 落库 + 置信度分流 + review + 自动建板（V4） | ✅ |
| T9 | 看板→Word 导出 | 聚合统计 + task-list/weekly-report 模板渲染 + 产物下载（V5） | ✅ |
| T10 | Tauri 桌面壳 | apps/desktop | ⬜ |
| T11 | Golden 回归集 + CI | conversion 测试集 + CI | ⬜ |
| T12 | WebSocket 协作基础（快照+全量拉取） | server/modules/collab | ⬜ |

---

## 2. 技术栈（硬性约束，不要提议替换）

| 项 | 选择 | 约束 |
|---|---|---|
| 后端语言 | **Java 25（LTS）** | 当前 WSL 已有 GraalVM 25（sdkman `25.0.2-graal` / `25.0.4-graal`），设为默认；勿用 JDK 8 |
| 构建工具 | **Gradle（wrapper 优先）** | **明确不用 Maven**；`server/` 用 Gradle 多模块或单模块按需 |
| 后端框架 | **Spring Boot 4.1.1** + Spring Modulith | 模块化单体（ADR-1）；Boot 4 基于 Spring Framework 7 / Jakarta EE 11，Web 用 `spring-boot-starter-webmvc`（不再是 `starter-web`）；**Servlet 模型，不用 Reactive/WebFlux（ADR-10）** |
| 数据库 | PostgreSQL 18（blocks 用 JSONB；PG 19 未 GA 不采用） | 连接串走环境变量 |
| 缓存 | Valkey 9.1.2（Redis 协议兼容，Linux 基金会托管，替代 Redis 7） | 会话/协作文档内存态 |
| 对象存储 | MinIO（S3 兼容） | 附件/转换产物 |
| 搜索 | Elasticsearch 8 | Phase 2 引入 |
| Web 前端 | Next.js 14/15 + React + TypeScript | App Router；编辑器/看板组件 `'use client'` |
| 桌面端 | Tauri 2（Rust 壳） | 复用 packages/core，不用 Electron |
| 移动端 | Flutter 3 | Phase 3；**在 Windows 侧开发**（WSL 不适合 Flutter 设备调试/工具链），MVP 只预留 API 契约 |
| 包管理 | **bun workspaces** | 根 `package.json` 的 `workspaces` 字段（`apps/*`、`packages/*`）；脚本统一用 `bun`/`bunx`，**不用 pnpm/npm/yarn** |
| Git 钩子 | **lefthook** | **不用 husky**（与 lanchat 项目一致，预编译二进制） |
| 提交校验 | @commitlint/cli + @commitlint/config-conventional | scope 白名单见 §5.1 |
| 实时协作 | Yjs（CRDT），服务端透传 + 二进制快照 | 服务端不解析 Yjs 内部结构（ADR-3） |
| LLM | OpenAI 兼容多 provider | 经 `LlmProvider` 接口抽象，禁止硬编码某家 |

**禁止引入**：Maven、husky、任何绕过 `LlmProvider` 的 LLM 调用、拼 SQL（一律参数化/JPA）、把密钥/Token 写入仓库。

**关键选型理由**：Gradle 是用户指定；lefthook 与 lanchat 项目验证过的坑一致（bun/pnpm 装的预编译二进制，不依赖 Node 运行时做钩子）。

---

## 3. 架构铁律（违反会被打回）

1. **模块化单体边界**：`server/modules/{module}`，跨模块只走 `api/` 接口与领域事件；禁止直接访问他模块 repository。
2. **Block 模型**：blocks 一律 `JSONB`；区块树用 `parent_id + children 数组` 维护，禁止全表重排。
3. **AI 转换异步化**：转换必须走异步任务（HTTP 返回 jobId）；规则优先（正则），LLM 只做语义抽取；输出必须带 `evidence`（来源段落引用）+ `confidence`；`confidence < 0.8` 强制人工校对。
4. **Yjs 透传**：服务端只转发协议消息 + 存快照（BYTEA），不解析 CRDT 内部结构。
5. **越权红线**：所有数据访问必须过 `workspace_id` 校验；卡片/文档/转换任务归属必须校验。
6. **端不直连库**：桌面/移动端不得绕过后端 API 直连数据库。
7. **版本号不入源码**：构建时注入（Gradle `-Pversion` / manifest），源码搜不到版本字符串。

---

## 4. 目录结构

```text
transnote/
├── apps/
│   ├── web/                  # Next.js
│   └── desktop/              # Tauri 2.x（引用 packages/core）
├── packages/
│   ├── core/                 # React 编辑器/看板组件、hooks、Zustand stores（不依赖 Next.js）
│   ├── api-client/           # OpenAPI 生成 + fetch/WS 封装
│   ├── schema/               # Block JSON Schema（双端代码生成源头）
│   └── ui/                   # 设计系统组件
├── mobile/                   # Flutter（Phase 3，Windows 侧开发，见 §7）
├── server/                   # Java 模块化单体（Gradle）
│   ├── app/                  # 启动器 + 配置
│   └── modules/              # identity/document/board/collab/conversion/search/notification/asset
├── docs/                     # 方案与交接文档（AI 必读上下文）
├── .agents/                  # AI Agent 协作配置（rules/workflow/tools/history）
└── infra/                    # docker-compose.yml、CI 脚本
```

**数据模型 / API / ADR 权威来源**：`docs/类Notion平台_MVP开发交接文档.md`（含完整 DDL、API 契约、转换实现要点）；Agent 角色与交接协议见 `docs/类Notion平台_AI-Agent开发规范.md`。

---

## 5. 提交规范

### 5.1 Conventional Commits + scope 白名单

```
<type>(<scope>): <subject>
```

**type**：`feat` `fix` `refactor` `perf` `test` `docs` `build` `ci` `chore` `revert`

**scope 只能是**：`identity` `document` `board` `conversion` `collab` `search` `notification` `asset` `core` `api-client` `schema` `ui` `web` `desktop` `mobile` `server` `docs` `deps` `ci` `repo` `release`

**subject**：≤72 字符，祈使句，不加句号。

```
✅ feat(conversion): 实现 Word→看板 抽取流水线
✅ fix(board): 修复拖拽换列后 position 重复
✅ chore(repo): 接入 lefthook 与 commitlint
❌ 更新代码
❌ feat: 加了一堆东西
❌ feat(Conversion): Add pipeline    （scope 拼错/大小写）
```

scope 白名单不是形式主义——它逼着每次提交想清楚「改的是哪个模块」，跨端仓库的可追溯性全靠它。改 scope 白名单必须同步改 `commitlint.config.js`（如果存在）和 `.agents/tools/check.sh` 的检查项。

### 5.2 原子化提交（硬性要求）

**定义**：一个 commit = 一个可独立理解、可独立回滚、可独立 cherry-pick 的变更单元。

| 规则 | 反例 |
|---|---|
| 一次只做一件事，不混 feat + refactor | `feat(document): 加版本号并重构权限` |
| 必须通过验证矩阵（见 `.agents/workflow/milestone-flow.md`） | 提交半成品 |
| 不留调试残留（无 System.out / console.log / 注释掉的代码块） | 提交里带 print 调试 |
| 生成物不入库（`build/`、`.gradle/`、`node_modules/`、`*.db` 已 gitignore） | 把编译产物提交 |
| **迁移与逻辑分离**：改表结构一个 commit，用新结构的业务逻辑下一个 commit | 一次性提交，回滚时炸掉 |

**提交前自检（三个问题）**：
1. subject 能不能一句话说清？说不清 → 拆
2. 出问题能单独 revert 这个 commit 吗？不能 → 拆
3. 里面有没有与 subject 无关的文件？有 → 拿出来（用 `git add -p` 分块暂存）

---

## 6. 版本与发布

- 已集成 **semantic-release**（参照 lanchat 现役配置）：`bun run release` 自动生成 `CHANGELOG.md` + 打语义化版本 tag；本地/MVP 阶段只做 CHANGELOG + git tag，**不推送 npm / GitHub**（接入远程仓库时补 `repositoryUrl` 与 `@semantic-release/github`，见 `release.config.js` 注释）。
- **不发版时不要手动改版本号，也不要手动打 tag**（版本号只由 semantic-release 从提交信息计算）。
- 起点标记：仓库首个 release 前已打 `git tag v0.0.0`（标记之后才开始累计变更）。
- 依赖版本（实测可跑，2026-09-06 已发 v0.0.1）：semantic-release `25.0.9` + `@semantic-release/changelog` `7.0.0` + `@semantic-release/git` `11.0.1` + `conventional-changelog-conventionalcommits` `9.3.1`（**必须根显式声明**——它是插件的 peerDependency，bun 不会自动装；9.3.1 是 release-notes-generator 官方锁定，与 writer 8.4.0 兼容；装 10.x 会报 `Missing helper`，早期「锁 ^8」经验已过时）。

---

## 7. 环境

- Arch Linux（WSL2），用户 `ppmb`，项目在 `~/code/transnote`（ext4）——**不要放到 `/mnt/c`**（9P 文件系统，构建慢且 inotify 失效）。
- Java 25（LTS）：sdkman 管理（当前有 `25.0.2-graal` / `25.0.4-graal` / `8.0.502-zulu`），**默认切到 25**（`sdk default java 25.0.4-graal`）；勿用 JDK 8。`JAVA_HOME` 需显式设置，Gradle 优先用 wrapper。
- Node `v26.7.0`（`/usr/sbin/node`）；bun 在 `~/.bun/bin`（**不在默认 PATH**，命令跑不通先 `export PATH="$HOME/.bun/bin:$PATH"`）；前端包管理用 **bun workspaces**。
- git 已配置 `user.name=皮皮萌宝` / `user.email=panda1943575780@outlook.com`。
- Docker：`infra/docker-compose.yml` 起 PostgreSQL / Valkey / MinIO（开发期）。
- **移动端（Flutter）在 Windows 侧开发**：flutter 工具链在 Windows `D:\programmer\flutter\bin`（Android SDK / adb / 模拟器全走 Windows）；WSL 内**不装不跑** Flutter 工具链。同一 git 仓库用 worktree 在 Windows 侧检出 `mobile` 相关分支开发（见 `.agents/workflow/scratchpad-concurrency.md`），提交归一到同一仓库；WSL 侧不直接写 `mobile/`。

**已踩过的坑 / 环境注意（持续追加）**：

| 坑 | 现象 | 正解 |
|---|---|---|
| Java 版本不满足 | Spring Boot 4.x 需要 Java 17+（推荐 25），JDK 8 直接编译失败 | `sdk default java 25.0.4-graal` 并设 JAVA_HOME |
| Windows 侧 JDK 混入 PATH | WSL PATH 含 `/mnt/d/programmer/java/jdk8u452-b09/bin`，`java` 不可用 | 在 WSL 内显式管理 JAVA_HOME，不依赖 Windows PATH |
| lefthook 子进程找不到命令 | SSH push / 钩子触发的子进程不继承交互式 PATH | 钩子命令里显式 `export PATH="$HOME/.bun/bin:$PATH"` 等 |
| bun 不自动安装 peer 依赖 | semantic-release 插件锁定的 `conventional-changelog-conventionalcommits` 缺失 → `Missing helper` / module not found | 根显式 `bun add -d conventional-changelog-conventionalcommits@9.3.1` |
| bun 的 prepare 环境无 node_modules/.bin | `"prepare": "lefthook install"` 报 command not found（npm 会注入 .bin，bun 不会） | prepare 写成 `"prepare": "bunx lefthook install"` |
| bun remove 误伤传递依赖 | `bun remove` 一个显式包后，其他显式 devDeps（如 commitlint）可能从 package.json 消失 | remove 后检查 package.json devDependencies 完整性，必要时重装 |

---

## 8. 命令速查

```bash
cd ~/code/transnote

bun install               # 安装前端/根依赖（首次或 lock 变更后）
bunx lefthook install     # 装 git 钩子（clone/首次后必做）
bunx commitlint --edit    # 校验提交信息（hook 会自动跑）

# server（Gradle，未生成 wrapper 前用系统 gradle 生成：gradle wrapper）
./gradlew bootRun         # 启动后端
./gradlew test            # 后端单测
./gradlew compileJava     # 编译检查

docker compose -f infra/docker-compose.yml up -d   # 起依赖（PG/Valkey/MinIO）

bash .agents/tools/check.sh   # 仓库一致性检查（多 Agent 协作必跑）
```

## 9. 改动流程

1. 先读 `AGENTS.md`「当前阶段」+ `.agents/rules/vision.md`（愿景与边界）+ `.agents/history/MEMORY.md`（ADR/待办）
2. **方案优先**：涉及架构/数据模型/API 的改动，先落 `docs/proposals/<date>-<topic>-proposal.md` 与用户确认，用户说「生成/搭建/开始做」再写实现代码
3. 改代码 → 跑验证矩阵（`.agents/workflow/milestone-flow.md`）→ `git add -p` 拆原子提交
4. 提交信息按 §5.1；提交后更新 `.agents/history/<yyyy-mm-dd>.md` 当日日志
5. **不要**在 commit message 里加 `Co-Authored-By` 之类的自动签名，除非用户要求

## 10. 不确定时

- 架构层面的取舍 → 先用文字讨论，得到共识再写代码；讨论结果记录在 commit / PR 描述
- 本文件有歧义或过时 → 直接改本文件，并在 commit message 里说明
- 用户没明确要求的重构/优化 → 不做（偏好最小改动）
- 与 `docs/` 方案文档冲突时 → 以用户最新口头决定为准，并回改两份文档

---

## 11. 专项：AI 转换质量（核心差异化，Phase 1 起适用）

- **Golden 回归**：`server/modules/conversion/src/test/resources/golden/` 放 20+ 样例 docx + 期望 JSON；LLM 相关变更必须跑，关键字段准确率阈值：title 95% / assignee 90% / due_date 90%。
- **可追溯**：每次转换记录 `llm_model + prompt_version + 原始抽取 JSON`，支持重放；prompt 变更必须递增 `prompt_version`。
- **人工校对门**：`confidence < 0.8` 的抽取项禁止自动入库，必须等用户 PATCH /review 确认。
- **隐私**：用户文档内容与校对数据不得写入日志或公开提示词样例。

### §8 环境坑表（2026-09-06 追加：Docker/PG18）

- **Docker 引擎在 Windows 侧（Docker Desktop）**：WSL 里 `docker` 是纯客户端；WSL 内 socket（`/mnt/wsl/docker-desktop/shared-sockets`）为 tmpfs `mode=755` root 独占，**WSL 内无法直连**（sudo 需密码也不可行）。统一用 Windows 侧 CLI：`& "C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose -f <win路径> up -d`；引擎就绪探测：`docker.exe info`。
- **.wslconfig 是 Mirrored 网络**：Windows 宿主端口在 WSL 内 `localhost:<port>` 直达（已验证 5433/6379/9000）。
- **Docker Hub 直连超时**：compose 镜像默认走 `IMAGE_PREFIX:-docker.m.daocloud.io` 加速（已验证可拉）；切官方源设 `IMAGE_PREFIX=` 空。Docker Desktop 的 settings-store.json 里 registryMirrors 字段名不对（新版不认），别改；改了还要小心 **PowerShell `Set-Content -Encoding UTF8` 会写 BOM**，Docker 解析报 `invalid character '茂'` 导致 backend 崩溃（用 `[IO.File]::WriteAllText(..., UTF8Encoding($false))`）。
- **PG 18 官方镜像挂载变更**：必须挂 `/var/lib/postgresql` 根（数据在子目录），挂旧路径 `/var/lib/postgresql/data` 直接报错退出。
- **宿主 5432 被 `ai-copilot-postgres-1` 占用**：transnote PG 定 **5433**（compose + application.yml + 文档 DB_URL 三处一致）。
- **本机其他项目容器**（ai-copilot/wakapi/pgadmin4 等）不要动，端口规划绕开。

### §10 Boot 4 测试与构建坑（2026-09-06 追加）

- **Gradle 9 需显式提供 JUnit Platform launcher**：纯 java 模块（如 identity）test 失败报 "Failed to load JUnit Platform" → `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`（版本 BOM 管）。
- **Boot 4 把 MockMvc 拆成独立 starter**：`spring-boot-starter-webmvc-test` 必须显式声明；且 `@AutoConfigureMockMvc` 包名改为 `org.springframework.boot.webmvc.test.autoconfigure`（不再是 ...web.servlet.autoconfigure）。
- **脚本验证命令禁接 grep 管道**（吞退出码）：统一 `if ! cmd > log 2>&1; then tail log; exit 1; fi` 模式。
- T2 工作区 API 约定（文档未定义，已按 §7.3 风格落地）：`POST/GET /api/v1/workspaces`、`GET/PATCH/DELETE /api/v1/workspaces/{id}`；错误 404（不存在）/422（校验、slug 冲突）。
- T7 抽取端点（契约未定义按 §7.3 风格自拟）：`POST /api/v1/conversions/extract`（multipart → tasks + promptVersion）；LLM 未启用回退规则抽取（rule-v1）；启用方式 `transnote.llm.enabled=true` + LLM_BASE_URL/LLM_API_KEY/LLM_MODEL。
- 装配约定：conversion 为纯领域模块（无 Spring 注解），TaskExtractor 等编排 Bean 在 webmvc 装配层注册（LlmConfig @Bean + ObjectProvider）。
- Jackson 3 的 `readTree` 抛 RuntimeException（无 IOException），catch 子句写 `IOException | RuntimeException` 会编译失败（不可达分支）。
- T6 解析端点（契约未定义，按 §7.3 风格自拟）：`POST /api/v1/conversions/parse`（multipart file → DocElement 树 + 分块），正式 word-to-board 流程（conversion_jobs 落库）在 T8。
- **POI 5.5.1 坑**：① XWPFParagraph 无 `getNumPr()`，走 `getCTP().getPPr().getNumPr()`；② CT 数值属性 `setVal` 需 BigInteger；③ 模块级 poi 依赖用 implementation 不透传，webmvc 集成测试要再 `testImplementation(libs.poi.ooxml)`；④ 新模块必须加 Boot BOM platform，否则 starter 版本解析为空。
- commitlint subject 禁首字母大写词（sentence/start-case）：subject 里英文词全小写或用全大写缩写（Word → word）。
- T5 看板 API（契约 §7.3）：`POST/GET /api/v1/boards`、`GET/PATCH/DELETE /{id}`、`POST /{id}/columns`、`DELETE /{id}/columns/{columnId}`、`POST/GET /{id}/cards`（筛选 columnId/assigneeId/priority）、`PATCH /{id}/cards/{cardId}`（**含 columnId/position = 拖拽一次提交**，可同时改字段）。已删除列筛选返回空列表而非 404（筛选语义）。
- T5 看板 API（契约 §7.3）：`POST/GET /api/v1/boards`、`GET/PATCH/DELETE /{id}`、`POST /{id}/columns`、`DELETE /{id}/columns/{columnId}`、`POST/GET /{id}/cards`（筛选 columnId/assigneeId/priority）、`PATCH /{id}/cards/{cardId}`（**含 columnId/position = 拖拽一次提交**，可同时改字段）。已删除列筛选返回空列表而非 404（筛选语义）。
- **Boot 4 MockMvc 包名变更**：`AutoConfigureMockMvc` 在 `org.springframework.boot.webmvc.test.autoconfigure`（Boot 3 的 `org.springframework.boot.test.autoconfigure.web.servlet` 已不存在）；依赖 `spring-boot-starter-webmvc-test`。
- T5 看板 API（契约 §7.3）：`POST/GET /api/v1/boards`、`GET/PATCH/DELETE /{id}`、`POST /{id}/columns`、`DELETE /{id}/columns/{columnId}`、`POST/GET /{id}/cards`（筛选 columnId/assigneeId/priority）、`PATCH /{id}/cards/{cardId}`（**含 columnId/position = 拖拽一次提交**，可同时改字段）。已删除列筛选返回空列表而非 404（筛选语义）。
- T3 文档/块 API（契约 §7.3 未定义处已按风格落地）：`POST/GET /api/v1/documents?workspaceId=`、`GET/PATCH/DELETE /{id}`、`PATCH /{id}/blocks`（updates: upsert|delete|move）；响应块节点含 position/version。GET versions 端点未实现（需快照表，T6 后置）。
- T8 转换链路：`POST /api/v1/conversions/word-to-board`（multipart file + workspaceId + targetBoardId?）→ 同步抽取分流（全部 ≥0.8 自动建板 COMPLETED，否则 REVIEW）；`GET jobs/{id}`、`PATCH jobs/{id}/review`（CONFIRMED/REJECTED，携带 taskTitle 视为 EDITED）、`GET jobs/{id}/result`。卡片写 source_evidence（[{paragraph_index, quote}] 原文追溯）+ assignee_name（无用户体系过渡字段）。
- T9 导出：`POST /api/v1/conversions/board-to-word?workspaceId=` body {boardId, template: task-list|weekly-report} → 同步聚合（完成率按列标题含"完成/done"判定；延期=dueDate 早于今天且非完成列）→ WordExporter 渲染（Heading1/2 + 任务表底纹 D9E2F3 + ☐/☑ + 页码 PAGE/NUMPAGES 域）→ LocalAssetStorage 落盘 → COMPLETED；result 返回 assetUrl（`GET /api/v1/conversions/assets/{id}/download`）；质量门 WordExporter.verify 回读，LibreOffice 缺失时跳过（§8.5 步骤 4 告警语义）。
- T9 坑：① HeaderFooterType 在 `org.apache.poi.wp.usermodel`（非 xwpf）；② 页码域：`run.getCTR().addNewInstrText().setStringValue("PAGE")`（无 setInstrText）；③ CTShd.getFill() 返回解码后 byte[]，断言须 HexFormat hex 编码后比对；④ 完成率整数时格式化掉 `.0`（50.0% → 50%）。
- T8 坑：① thenReturn 实参内禁止任何 mockito stubbing（先建对象再 stub）；② 已应用迁移改 DDL 后 Flyway validate 失败 → 开发库 `DROP SCHEMA public CASCADE; CREATE SCHEMA public` 重建重放（或 repair）；③ evidence/sourceEvidence 是 JSONB 字符串，jsonPath 断言用 containsString；④ @CreationTimestamp 字段必须同步在 V 迁移建列；⑤ ConversionJob promptVersion 在 complete 时取自 extractor.getLastPromptVersion()，不能读 job 自身。
- T10 Golden：样例生成器写 build/golden-gen（勿用 @TempDir，JUnit 随机清理）；expected.json 为期望清单；阈值统计口径=期望任务顺序比对，多余抽取容忍；g16 空勾选放末尾。
- T4a 前端：① bun install --frozen-lockfile 首次必须更新 lockfile（先裸跑一次再锁）；② Next 产物 .next/ 必须进 .gitignore（曾误提交 242 文件）；③ api-client request() 全调用须传 baseUrl；④ 含 $ 的 bash 命令必须写 .sh 经 wsl -e bash 执行（PowerShell 会吞 $ 变量）；⑤ 前端 CORS 由 server WebConfig 放行 localhost:3000/tauri。

### §11 Boot 4 / PG18 深度坑（T3 实测 2026-09-07）

- **Boot 4 原生 Jackson 3（tools.jackson.\*）**：Jackson 2 自动配置拆到可选 `spring-boot-jackson2`（deprecated）；模块注入 ObjectMapper 用 `tools.jackson.databind.ObjectMapper`，捕获异常用 `tools.jackson.core.JacksonException`（无 JsonProcessingException）；依赖坐标 `tools.jackson.core:jackson-databind`（BOM 管版本）。
- **PG jsonb 绑定 42804**：String 字段 + `columnDefinition="jsonb"` 直接 setString 报 "column is of type jsonb but expression is of type character varying" → 字段必须加 `@JdbcTypeCode(SqlTypes.JSON)`。
- **Spring Data 派生查询**：`findByWorkspaceId` 在实体只有 `workspace` 关联时报 UnknownPathException → 显式 `findByWorkspace_Id`。
- **@Version 初始值**：INSERT 后 version=0；要"从 1 起"字段初始化 `= 1`。注意 addChild/move 重排（saveAll）也会让父块 version 递增——断言时按实际更新次数算。
- **uuid[] 映射**：`List<UUID>` + `@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition="uuid[]"` 可用（T3 已验证）。
- T3 文档/块 API（契约 §7.3 未定义处已按风格落地）：`POST/GET /api/v1/documents?workspaceId=`、`GET/PATCH/DELETE /{id}`、`PATCH /{id}/blocks`（updates: upsert|delete|move）；响应块节点含 position/version。GET versions 端点未实现（需快照表，T6 后置）。

### §11 Boot 4 / PG18 深度坑（T3 实测 2026-09-07）

- **Boot 4 原生 Jackson 3（tools.jackson.\*）**：Jackson 2 自动配置拆到可选 `spring-boot-jackson2`（deprecated）；模块注入 ObjectMapper 用 `tools.jackson.databind.ObjectMapper`，捕获异常用 `tools.jackson.core.JacksonException`（无 JsonProcessingException）；依赖坐标 `tools.jackson.core:jackson-databind`（BOM 管版本）。
- **PG jsonb 绑定 42804**：String 字段 + `columnDefinition="jsonb"` 直接 setString 报 "column is of type jsonb but expression is of type character varying" → 字段必须加 `@JdbcTypeCode(SqlTypes.JSON)`。
- **Spring Data 派生查询**：`findByWorkspaceId` 在实体只有 `workspace` 关联时报 UnknownPathException → 显式 `findByWorkspace_Id`。
- **@Version 初始值**：INSERT 后 version=0；要"从 1 起"字段初始化 `= 1`。注意 addChild/move 重排（saveAll）也会让父块 version 递增——断言时按实际更新次数算。
- **uuid[] 映射**：`List<UUID>` + `@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition="uuid[]"` 可用（T3 已验证）。
- T3 文档/块 API（契约 §7.3 未定义处已按风格落地）：`POST/GET /api/v1/documents?workspaceId=`、`GET/PATCH/DELETE /{id}`、`PATCH /{id}/blocks`（updates: upsert|delete|move）；响应块节点含 position/version。GET versions 端点未实现（需快照表，T6 后置）。

### §11 Boot 4 / PG18 深度坑（T3 实测 2026-09-07）

- **Boot 4 原生 Jackson 3（tools.jackson.\*）**：Jackson 2 自动配置拆到可选 `spring-boot-jackson2`（deprecated）；模块注入 ObjectMapper 用 `tools.jackson.databind.ObjectMapper`，捕获异常用 `tools.jackson.core.JacksonException`（无 JsonProcessingException）；依赖坐标 `tools.jackson.core:jackson-databind`（BOM 管版本）。
- **PG jsonb 绑定 42804**：String 字段 + `columnDefinition="jsonb"` 直接 setString 报 "column is of type jsonb but expression is of type character varying" → 字段必须加 `@JdbcTypeCode(SqlTypes.JSON)`。
- **Spring Data 派生查询**：`findByWorkspaceId` 在实体只有 `workspace` 关联时报 UnknownPathException → 显式 `findByWorkspace_Id`。
- **@Version 初始值**：INSERT 后 version=0；要"从 1 起"字段初始化 `= 1`。注意 addChild/move 重排（saveAll）也会让父块 version 递增——断言时按实际更新次数算。
- **uuid[] 映射**：`List<UUID>` + `@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition="uuid[]"` 可用（T3 已验证）。

### §9 Flyway 迁移约定（2026-09-06 定）

- **schema 变更只能写 `db/migration/V*.sql`**（`spring-boot-starter-flyway` + 显式覆盖 `flyway-core`/`flyway-database-postgresql` 12.11.0）；`hibernate.ddl-auto: none`。
- 迁移命名 `V<序号>__<描述>.sql`，一次迁移一个可回滚主题；**已应用的迁移文件禁止改动**（校验失败走新迁移修复）。
- 坑：**Boot 4.1.1 BOM 的 flyway 12.4.0 不支持 PG18**（报 `Unsupported Database: PostgreSQL 18.6`）；必须 toml 显式覆盖 **12.11.0**，且 Flyway 10+ 的 PostgreSQL 支持是独立模块 `flyway-database-postgresql`（仅 starter 不含）。
- 坑：shell 脚本里 `cmd | grep | head` 管道会吞掉 `set -e` 的失败退出码——构建失败也会继续提交。验证类命令不要接 grep 管道，或检查 `${PIPESTATUS[0]}`。
- 已完成：V1 workspace 表（id uuid PK default gen_random_uuid / name / slug unique / created_at / updated_at）；认证后置无 owner_id。

## 仓库管理（GitHub 接入）

- **远程**：`github` = git@github.com:pandaymx/transnote.git（私有，ssh）；`origin` = 本地 bare 备份 `~/code/transnote.git`。推送双写：`git push github main && git push origin main`（或先 github 后由 release 提交 fetch 回来再推 origin）。
- **CI**（.github/workflows/ci.yml，push main / PR 触发，3 job 全绿基线）：
  - Server build & test：postgres:18 service container + temurin 25（setup-java v5）+ spotlessCheck + clean build；数据源经 SPRING_DATASOURCE_URL 覆盖为 5432（本地 5433）。
  - Commit message lint：bun install --frozen-lockfile + commitlint --from HEAD~20。
  - Semantic release：仅 push main，needs 前两者；permissions 需 contents/issues/pull-requests 全 write（success 评论步骤缺权限会失败）；产出 CHANGELOG + tag + GitHub Release（release.config.js repositoryUrl 已指向 GitHub）。
- **发版**：feat → minor（v1.0.0 已发布，commit 874bb2b 触发）；fix → patch；`[skip ci]` 提交不触发 CI 循环。
- WSL 推送用 ssh（gh 已配置 "Arm WSL SSH" key）；本地腾讯镜像 gradle wrapper 在 CI 可正常访问。
| T10 | Golden 回归集 | 22 样例 title100/assignee100/due100（阈值95/90/90） | ✅ |
