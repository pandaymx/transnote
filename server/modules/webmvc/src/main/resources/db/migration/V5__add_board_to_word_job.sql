-- T9：看板→Word 导出（契约 §8.5）。偏差说明：
--   source_board_id：board-to-word 的源看板（word-to-board 时为 NULL）
--   result_asset_id：导出产物资产（.docx），result 端点返回其下载 URL
--   template：task-list / weekly-report（§8.5 内容组织模板）

ALTER TABLE conversion_jobs ADD COLUMN source_board_id UUID;
ALTER TABLE conversion_jobs ADD COLUMN result_asset_id UUID;
ALTER TABLE conversion_jobs ADD COLUMN template VARCHAR(32);
CREATE INDEX idx_conversion_jobs_source_board ON conversion_jobs (source_board_id);
