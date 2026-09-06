-- TransNote V1：工作区表
-- 认证后置（ADR-11）：不建 users/workspace_members，无 owner_id；T2.1 认证时再迁
-- 约定：所有 schema 变更只能通过 db/migration 下的迁移脚本
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(64) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
