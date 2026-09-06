// TransNote 发布配置（semantic-release）
// 参照 lanchat 现役配置；本地/MVP 阶段：只生成 CHANGELOG + git tag，不推送 npm/GitHub
// 接入远程仓库时：补 repositoryUrl、@semantic-release/github（需 GITHUB_TOKEN）
module.exports = {
  branches: ['main'],
  // 本地 bare 仓库占位（无 GitHub 前 origin 指向 ~/code/transnote.git）。
  // release-notes-generator 会对该 URL 执行 new URL()，本地路径会报 Invalid URL。
  // 接入 GitHub 时替换为真实 URL（如 https://github.com/<owner>/transnote.git）并加 @semantic-release/github 插件。
  repositoryUrl: 'file:///home/ppmb/code/transnote.git',
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
