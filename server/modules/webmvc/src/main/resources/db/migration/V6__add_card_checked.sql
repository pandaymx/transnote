-- V6: 卡片级完成态（Notion 代办勾选）。默认 false；Word→看板建卡时按目标列完成态初始化。
ALTER TABLE board_cards ADD COLUMN checked boolean NOT NULL DEFAULT false;
