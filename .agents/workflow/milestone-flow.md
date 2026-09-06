# 里程碑推进 SOP（Agent 工作流）

> 用户工作方式：**方案优先、按需生成**。默认只产出方案与文档，等用户说「生成 / 搭建 / 开始做」再写实现代码。

## 标准流程

1. **读上下文**：`AGENTS.md` 当前阶段 + `.agents/rules/vision.md`（愿景与边界）+ MEMORY.md
2. **摸底**：verify-don't-assume —— 用 grep / git log / 实际读文件证实现状，不凭印象断言
3. **方案优先**：涉及架构/数据模型/API 的改动，proposal 落盘 `docs/proposals/<date>-<topic>-proposal.md`（不入库，`.git/info/exclude` 本地排除）
4. **用户拍板**：等用户选定方案后再进入实现
5. **原子提交**：按 AGENTS.md §5.2 三问拆 commit（一句话说清？可单独 revert？无无关文件？）
6. **验证矩阵**：见下节，全绿才提交/推送
7. **文档同步**：AGENTS.md + docs/ 同步实际进度（长文档滞后是真实反馈，用户会提醒）
8. **发版**：MVP 阶段不接自动发版；不发版时不手动改版本号 / 打 tag

## 验证矩阵（默认全量）

| 检查 | 命令 | 说明 |
|---|---|---|
| 后端编译 | `./gradlew compileJava` | 0 错误（Gradle，不用 Maven） |
| 后端单测 | `./gradlew test` | 全绿 |
| 前端类型/lint | 对应包内 `bunx tsc --noEmit` / `bunx eslint` | 前端就绪后生效 |
| 仓库一致性 | `bash .agents/tools/check.sh` | 多 Agent 协作必跑 |
| 转换回归 | `./gradlew :modules:conversion:test`（golden 集） | LLM 相关变更必跑 |
| 冒烟 | `docker compose -f infra/docker-compose.yml up -d` + 起服务实测 | 浏览器/API 实际看效果 |

## 已知坑防御（开发中持续追加，避免重复踩）

| 坑 | 现象 | 正解 |
|---|---|---|
| Java 版本不对 | Spring Boot 4.x 编译失败（JDK 8 或缺 JAVA_HOME） | `sdk default java 25.0.4-graal` 并设 JAVA_HOME；Gradle 用 wrapper |
| lefthook 命令找不到 | 钩子子进程 PATH 不含 bun（`~/.bun/bin`） | 钩子命令显式 `export PATH="$HOME/.bun/bin:$PATH"`（见 AGENTS.md §7） |
| 测试命令用管道 | `./gradlew test \| tail` 退出码来自 tail，FAIL 也显示成功 | 必须 `set -o pipefail` 或不用管道 |
| 编辑器缓冲还原文件 | 关键改动用 Write 整文件落盘后被还原 | 改完 grep / gradlew 验证；重要文件用磁盘直写 |

## 提交规范速查

- `type(scope): subject`，scope 白名单见 AGENTS.md §5.1：`identity document board conversion collab search notification asset core api-client schema ui web desktop mobile server docs deps ci repo release`
- subject ≤72 字符，祈使句，不加句号；不加 Co-Authored-By 自动签名
