-- T3：文档与块（块级编辑器数据模型，契约 §7.2）
-- 偏差说明（vs 交接文档 DDL）：
--   1) documents.workspace_id 增加 ON DELETE CASCADE（删工作区级联文档，否则 FK 报错）
--   2) blocks 增加 position INT：契约 DDL 仅 children UUID[] 存子级顺序，根级块顺序无载体，此处为必要扩展
--   3) created_by 后置（认证 T2.1 后补，与 workspaces 一致）

CREATE TABLE documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  title VARCHAR(500) NOT NULL DEFAULT '',
  icon VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_workspace ON documents (workspace_id);

CREATE TABLE blocks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  parent_id UUID REFERENCES blocks(id) ON DELETE CASCADE,
  type VARCHAR(32) NOT NULL,
  content JSONB NOT NULL DEFAULT '{}',
  properties JSONB NOT NULL DEFAULT '{}',
  children UUID[] NOT NULL DEFAULT '{}',
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_blocks_doc ON blocks (document_id);
CREATE INDEX idx_blocks_parent ON blocks (parent_id);
