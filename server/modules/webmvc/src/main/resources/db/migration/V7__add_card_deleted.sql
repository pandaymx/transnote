-- V7: 卡片软删除（回收站：Notion 删除可恢复）
ALTER TABLE board_cards ADD COLUMN deleted boolean NOT NULL DEFAULT false;
CREATE INDEX idx_board_cards_deleted ON board_cards (board_id) WHERE deleted = true;
