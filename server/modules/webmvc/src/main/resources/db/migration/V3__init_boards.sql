-- T5：看板（契约 §7.2）。偏差说明（vs 交接文档 DDL）：
--   1) 补充 created_at/updated_at 时间戳与常用索引
--   2) created_by 后置（认证 T2.1 后补，与 workspaces/documents 一致）
--   3) boards.workspace_id 增加 ON DELETE CASCADE（删工作区级联看板）

CREATE TABLE boards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  layout VARCHAR(16) NOT NULL DEFAULT 'kanban',  -- kanban/list/calendar
  config JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_boards_workspace ON boards (workspace_id);

CREATE TABLE board_columns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  title VARCHAR(128) NOT NULL,
  position INT NOT NULL,
  status_color VARCHAR(16) DEFAULT '#8BC8EA',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_board_columns_board ON board_columns (board_id);

CREATE TABLE board_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  board_id UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  column_id UUID NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
  position INT NOT NULL,
  title VARCHAR(500) NOT NULL,
  description JSONB NOT NULL DEFAULT '{}',      -- 卡片内富文本（块结构，同 blocks.content）
  assignee_id UUID,
  due_date DATE,
  priority SMALLINT NOT NULL DEFAULT 1,          -- 0低/1中/2高/3紧急
  labels VARCHAR(32)[] NOT NULL DEFAULT '{}',
  source_document_id UUID,                       -- 来自哪个 Word 文档（追溯）
  source_evidence JSONB,                         -- [{paragraph_index, quote}] 来源引用
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_board_cards_board ON board_cards (board_id);
CREATE INDEX idx_board_cards_column ON board_cards (column_id);
