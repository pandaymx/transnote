## [1.4.0](https://github.com/pandaymx/transnote/compare/v1.3.0...v1.4.0) (2026-09-06)

### Features

* **conversion:** 转换历史列表接口与前端历史区块 ([1f3e6bc](https://github.com/pandaymx/transnote/commit/1f3e6bc0824b6d5aeadc0717323a45cb287b141c))
* **web:** 看板页导出 word 入口与 convert 页看板自动带入 ([47061a9](https://github.com/pandaymx/transnote/commit/47061a91b9b5721a2484657d16fed7361ba8e4f4))

## [1.3.0](https://github.com/pandaymx/transnote/compare/v1.2.2...v1.3.0) (2026-09-06)

### Features

* **web:** 看板卡片拖拽换列与内联编辑（乐观更新） ([9bcc378](https://github.com/pandaymx/transnote/commit/9bcc378944a3dceeab034a2425eaa825839c9768))

## [1.2.2](https://github.com/pandaymx/transnote/compare/v1.2.1...v1.2.2) (2026-09-06)

### Bug Fixes

* **desktop:** 声明 rust lib 名与移动端 crate-type ([fe07025](https://github.com/pandaymx/transnote/commit/fe07025cd812376263bf676cb9e9f38e8f9064ca))

## [1.2.1](https://github.com/pandaymx/transnote/compare/v1.2.0...v1.2.1) (2026-09-06)

### Bug Fixes

* **desktop:** 补充 tauri 占位图标修复 cargo check ([dd06759](https://github.com/pandaymx/transnote/commit/dd06759d93b11c330ac55de56ea02e25ebe51754))

## [1.2.0](https://github.com/pandaymx/transnote/compare/v1.1.0...v1.2.0) (2026-09-06)

### Features

* **desktop:** tauri 2 桌面壳骨架（vite+react，复用 packages/core）与 ci rust 编译 job ([597cf20](https://github.com/pandaymx/transnote/commit/597cf20635cbc7849dd5790c5e7b8df01d96507c))

## [1.1.0](https://github.com/pandaymx/transnote/compare/v1.0.0...v1.1.0) (2026-09-06)

### Features

* **server:** 本地开发 cors 允许 web 与 tauri 调用 ([a384071](https://github.com/pandaymx/transnote/commit/a3840714775ce89524706b459309f7d14fb1747e))
* **web:** next.js 应用骨架与 api-client/core 状态层（工作区/看板/转换页） ([81ea3ba](https://github.com/pandaymx/transnote/commit/81ea3ba4aee9875187196b3a8d8fe4230e69edd1))

## 1.0.0 (2026-09-06)

### Features

* **board:** 看板模块（V3 迁移 + 实体/仓库 + 列/卡片 CRUD 与拖拽服务 + 单测） ([70691e9](https://github.com/pandaymx/transnote/commit/70691e95e19c99c406682f07b4a6a6e7a583d2cf))
* **conversion:** llm provider 抽象与任务抽取（schema 约束 + 规则回退 + 单测） ([d781050](https://github.com/pandaymx/transnote/commit/d7810503ac4841079ce66a1f00664f2c030cf7eb))
* **conversion:** word 转看板任务落库（job/items + 置信度分流 + 自动建板） ([25539df](https://github.com/pandaymx/transnote/commit/25539df69c018c9aef1a99f030a23ed5c932e7f4))
* **conversion:** 实现 word 解析与分块（结构元素树 + 解析器 + 分块器 + 单测） ([cbc5086](https://github.com/pandaymx/transnote/commit/cbc50863651533f6a4f7553d51974c2c8bfafed9))
* **conversion:** 看板导出 word 渲染（聚合统计 + 表格 + 页码 + 回读校验） ([76fbcd1](https://github.com/pandaymx/transnote/commit/76fbcd1d855d1dac72a8eeaced39176d05f88698))
* **document:** 文档块模块（V2 迁移 + 实体/仓库 + CRUD 服务与单测） ([5068b80](https://github.com/pandaymx/transnote/commit/5068b80d79b16e8331f872594f07a965a76f5b6f))
* **identity:** 工作区 CRUD（实体/仓库/服务/REST API 与测试） ([fc81ba7](https://github.com/pandaymx/transnote/commit/fc81ba761b753acbe5ce00baff4e829b3cef0a21))
* **server:** word 解析调试端点（上传 docx 返回结构元素树与分块）与集成测试 ([d39e439](https://github.com/pandaymx/transnote/commit/d39e439c511d7d1c70192d2128b7781716755e73))
* **server:** 任务抽取端点与 llm 配置装配（未启用时规则回退）与集成测试 ([c0923fa](https://github.com/pandaymx/transnote/commit/c0923fa40378ecfb5d12a1ec1adaf70b0d5003f0))
* **server:** 接入 Flyway 迁移并新增 workspace 表 ([fe87a19](https://github.com/pandaymx/transnote/commit/fe87a1973fb9ab7a08de736f18a13c172b654e54))
* **server:** 文档/块 REST 端点（创建/树/批量块操作）与集成测试 ([5efe9e3](https://github.com/pandaymx/transnote/commit/5efe9e3dd9b6358e2241cbeae3adf1e798a396ad))
* **server:** 看板 REST 端点（板/列/卡片 CRUD + 拖拽一次提交 + 筛选）与集成测试 ([154b810](https://github.com/pandaymx/transnote/commit/154b810426a9841263fe07781015a0e9f395d9be))
* **server:** 看板转 word 端点与产物下载（result 返回 assetUrl） ([545a7be](https://github.com/pandaymx/transnote/commit/545a7be079d5a000e3daaa80479589f8cf480988))
* **server:** 转换任务端点（提交/查询/校对/结果）与集成测试 ([5bdb01c](https://github.com/pandaymx/transnote/commit/5bdb01c2e3dd81bf89cf70e3d15bda5f02e8712f))

### Bug Fixes

* **board:** 已删除列筛选返回空列表而非 404 ([6a7d66b](https://github.com/pandaymx/transnote/commit/6a7d66b5975734a4b3ff91b5d0dd9c892eb3805b))
* **release:** repositoryUrl 改用 file:// 本地占位，修复 new URL 报错 ([bb4cc8c](https://github.com/pandaymx/transnote/commit/bb4cc8c5ce895b4094c3e24334e3f91d42926ab3))
* **repo:** 修复 check.sh scope 比对与密钥扫描正则 ([35e73e3](https://github.com/pandaymx/transnote/commit/35e73e3d09c64f79172ed1aa507b5a3a7266c691))
* **repo:** 恢复 commitlint 依赖并修复 bun 下 prepare 脚本 ([d631f99](https://github.com/pandaymx/transnote/commit/d631f99c0da6dc3b920f519b1c4700b64f9c7b34))
* **repo:** 过滤 scope 提取中的空行，修复一致性比对 ([67b38a7](https://github.com/pandaymx/transnote/commit/67b38a747dfa7920296241521a2bdfaddf2bc8b4))

## [0.0.1](https:/home/ppmb/code/transnote/compare/v0.0.0...v0.0.1) (2026-09-06)

### Bug Fixes

* **release:** repositoryUrl 改用 file:// 本地占位，修复 new URL 报错 ([bb4cc8c](https:/home/ppmb/code/transnote/commit/bb4cc8c5ce895b4094c3e24334e3f91d42926ab3))
* **repo:** 过滤 scope 提取中的空行，修复一致性比对 ([67b38a7](https:/home/ppmb/code/transnote/commit/67b38a747dfa7920296241521a2bdfaddf2bc8b4))
* **repo:** 恢复 commitlint 依赖并修复 bun 下 prepare 脚本 ([d631f99](https:/home/ppmb/code/transnote/commit/d631f99c0da6dc3b920f519b1c4700b64f9c7b34))
* **repo:** 修复 check.sh scope 比对与密钥扫描正则 ([35e73e3](https:/home/ppmb/code/transnote/commit/35e73e3d09c64f79172ed1aa507b5a3a7266c691))
