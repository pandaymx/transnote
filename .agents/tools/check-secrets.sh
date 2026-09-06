#!/usr/bin/env bash
# pre-commit 密钥泄漏扫描：阻止 AK/SK / 私钥 / 敏感 token 入库
# 高置信模式（避免对业务字段误报）；命中即阻止提交，确认误报用 git add -p 分块排除
set -u
PATTERNS=(
  'AKIA[0-9A-Z]{16}'
  'sk-[A-Za-z0-9]{20,}'
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'
)
FOUND=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  for p in "${PATTERNS[@]}"; do
    # -e 防止以 "-" 开头的模式（如私钥头）被 grep 当作选项
    if grep -Eqi -e "$p" "$f"; then
      echo "⚠️ 疑似密钥泄漏：$f （模式：$p）"
      FOUND=1
    fi
  done
done < <(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(ts|tsx|js|jsx|json|md|yaml|yml|properties|sh|java|env.*)$' || true)

if [ "$FOUND" -eq 0 ]; then
  exit 0
fi
echo "❌ 检测到疑似密钥，已阻止提交。确认无害请 git add -p 分块排除后重试。"
exit 1
