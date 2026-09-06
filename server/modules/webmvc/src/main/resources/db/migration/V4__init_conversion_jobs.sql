-- T8：转换任务落库（契约 §7.2 conversion_jobs/items）。偏差说明（vs 交接文档 DDL）：
--   1) user_id 移除（认证 T2.1 后置，与 workspaces 一致）
--   2) workspace_id 加 ON DELETE CASCADE；补时间戳与索引
--   3) board_cards.assignee_name：T8 决策——无用户体系前存人名（assignee_id 保留待 T2.1）

CREATE TABLE conversion_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  direction VARCHAR(16) NOT NULL,               -- WORD_TO_BOARD / BOARD_TO_WORD
  source_asset_id UUID,
  source_document_id UUID,
  file_name VARCHAR(255),                       -- 上传原文件名（自动建板标题来源）
  target_board_id UUID,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',-- PENDING/EXTRACTING/REVIEW/COMPLETED/FAILED
  confidence JSONB NOT NULL DEFAULT '{}',
  llm_model VARCHAR(64),
  prompt_version VARCHAR(32),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
CREATE INDEX idx_conversion_jobs_workspace ON conversion_jobs (workspace_id);

CREATE TABLE conversion_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES conversion_jobs(id) ON DELETE CASCADE,
  task_title VARCHAR(500) NOT NULL,
  description TEXT,
  assignee VARCHAR(128),
  due_date DATE,
  priority SMALLINT,
  category VARCHAR(64),
  depends_on TEXT,
  evidence JSONB NOT NULL DEFAULT '{}',          -- [{paragraph_index, quote}]
  confidence NUMERIC(4,3) NOT NULL DEFAULT 0,   -- 0~1
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING/CONFIRMED/REJECTED/EDITED
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversion_items_job ON conversion_items (job_id);

-- 看板卡片补充负责人人名（无用户体系前的过渡字段）
ALTER TABLE board_cards ADD COLUMN assignee_name VARCHAR(128);
