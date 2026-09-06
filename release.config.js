// TransNote 发布配置（semantic-release）
// 参照 lanchat 现役配置；本地/MVP 阶段：只生成 CHANGELOG + git tag，不推送 npm/GitHub
// 接入远程仓库时：补 repositoryUrl、@semantic-release/github（需 GITHUB_TOKEN）
module.exports = {
  branches: ['main'],
  plugins: [
    ['@semantic-release/commit-analyzer', { preset: 'conventionalcommits' }],
    ['@semantic-release/release-notes-generator', { preset: 'conventionalcommits' }],
    ['@semantic-release/changelog', { changelogFile: 'CHANGELOG.md' }],
    [
      '@semantic-release/git',
      {
        assets: ['CHANGELOG.md'],
        message: 'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}'
      }
    ]
  ]
};
