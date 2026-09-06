#!/usr/bin/env bash
# TransNote 仓库一致性检查（多 AI Agent 协作用）
#
# 用法：仓库根目录执行  bash .agents/tools/check.sh
# 覆盖历史踩坑类型，防止任何 agent 会话重新踩：
#   1. commitlint scope 白名单与 AGENTS.md §5.1 声明不一致
#   2. docs/ 方案文档缺失（AI 必读上下文）
#   3. pnpm-workspace.yaml 声明的目录不存在
#   4. 密钥文件（.env*）被 git 跟踪
#
# 零依赖：仅用标准库 grep/sed/awk/comm，WSL Arch Linux 直接可跑。
# 新增检查项时保持「输出值 + 判定」结构，方便多 agent 快速定位。
# 兼容仓库尚未 git init 的状态：git 相关检查自动跳过。

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || { echo "❌ 无法进入仓库根目录 $ROOT"; exit 1; }

FAIL=0
PASS=0
ok()  { PASS=$((PASS + 1)); echo "  ✅ $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  ❌ $1"; }

# ---------- 1. commitlint scope 白名单与 AGENTS.md 一致性 ----------
echo "== 1. commitlint scope 白名单 vs AGENTS.md =="
if [ -f commitlint.config.js ]; then
  # GNU sed 的范围会多打印一行（subject-max-length），故排除键名/severity 词；
  # 代码围栏 ``` 会被 grep -o 匹配出空串，先过滤空行
  cfg_scopes="$(sed -n "/'scope-enum'/,/]/p" commitlint.config.js | grep -o "'[a-z-]*'" | tr -d "'" | grep -v '^$' | grep -vE '^(scope-enum|always|subject-max-length)$' | sort -u)"
  md_scopes="$(sed -n '/scope 只能是/,/^```$/p' AGENTS.md | grep -o '`[a-z-]*`' | tr -d '`' | grep -v '^$' | sort -u)"
  if [ "$cfg_scopes" = "$md_scopes" ]; then
    ok "scope 白名单一致（$(echo "$md_scopes" | tr '\n' ' ')）"
  else
    bad "scope 不一致：commitlint.config.js 独有=[$(comm -23 <(echo "$cfg_scopes") <(echo "$md_scopes") | tr '\n' ' ')]，AGENTS.md 独有=[$(comm -13 <(echo "$cfg_scopes") <(echo "$md_scopes") | tr '\n' ' ')]"
  fi
else
  ok "commitlint.config.js 尚未创建（Phase 0，跳过一致性比对）"
fi

# ---------- 2. docs/ 方案文档存在性 ----------
echo "== 2. docs/ 方案文档 =="
for f in docs/类Notion平台_MVP开发交接文档.md docs/类Notion平台_AI-Agent开发规范.md; do
  if [ -f "$f" ]; then
    ok "存在 $f"
  else
    bad "缺失 $f（AI 必读上下文）"
  fi
done

# ---------- 3. bun workspaces 目录存在性 ----------
echo "== 3. bun workspaces 目录 =="
if grep -q '"workspaces"' package.json 2>/dev/null; then
  for d in apps/web apps/desktop packages/core packages/api-client packages/schema packages/ui server mobile; do
    if [ -d "$d" ]; then
      ok "存在 $d/"
    else
      bad "缺失目录 $d/（workspace 声明但未创建）"
    fi
  done
else
  ok "package.json 尚未声明 workspaces（Phase 0，跳过目录检查）"
fi

# ---------- 4. 密钥文件被 git 跟踪 ----------
echo "== 4. 密钥不入库 =="
if [ -d .git ]; then
  tracked_env="$(git ls-files 2>/dev/null | grep -E '^\.env($|\.)' | head -5)"
  if [ -z "$tracked_env" ]; then
    ok "无 .env* 被 git 跟踪"
  else
    bad "以下 .env* 已被跟踪，应立即移除：$tracked_env"
  fi
else
  ok "仓库尚未 git init（跳过）"
fi

# ---------- 5. AGENTS.md 关键章节 ----------
echo "== 5. AGENTS.md 结构 =="
for sec in "## 1. 项目是什么" "## 2. 技术栈" "## 3. 架构铁律" "## 5. 提交规范"; do
  if grep -qF "$sec" AGENTS.md 2>/dev/null; then
    ok "AGENTS.md 含 $sec"
  else
    bad "AGENTS.md 缺 $sec"
  fi
done

echo
echo "结果：$PASS 通过 / $FAIL 失败"
[ "$FAIL" -eq 0 ] || { echo "❌ 存在失败项，修复后再继续。"; exit 1; }
echo "✅ 仓库一致性 OK"
