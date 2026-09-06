// TransNote 发布配置（semantic-release）
// main 分支 push 时由 GitHub Actions 触发：生成 CHANGELOG + git tag + GitHub Release
module.exports = {
  branches: ['main'],
  repositoryUrl: 'https://github.com/pandaymx/transnote.git',
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
    ],
    ['@semantic-release/github', {}]
  ]
};
