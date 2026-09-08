-- 卡片级块溯源：勾选完成回写源文档 todo 块（V9）
ALTER TABLE board_cards ADD COLUMN source_block_id uuid;
