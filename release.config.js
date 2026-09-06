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
    [
      '@semantic-release/github',
      {
        // Release 创建时附带安装包（GitHub 发布后 asset 不可再增补，必须创建时带上）
        assets: [
          { path: 'release-assets/linux/TransNote_*_amd64.deb', name: 'TransNote-linux-amd64.deb' },
          { path: 'release-assets/linux/TransNote-*.rpm', name: 'TransNote-linux-x86_64.rpm' },
          { path: 'release-assets/windows/*-setup.exe', name: 'TransNote-windows-setup.exe' },
          { path: 'release-assets/windows/*.msi', name: 'TransNote-windows-x64.msi' },
        ],
      },
    ]
  ]
};
