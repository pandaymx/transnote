// TransNote 提交规范（权威来源：AGENTS.md §5.1）
// 注意：修改 type/scope 白名单必须同步 AGENTS.md §5.1 与 .agents/tools/check.sh
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', ['feat','fix','refactor','perf','test','docs','build','ci','chore','revert']],
    'scope-enum': [2, 'always', ['identity','document','board','conversion','collab','search','notification','asset','core','api-client','schema','ui','web','desktop','mobile','server','docs','deps','ci','repo','release']],
    'subject-max-length': [2, 'always', 72]
  }
};
