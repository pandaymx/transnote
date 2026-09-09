package com.transnote.shared.event;

import java.util.UUID;

/**
 * 文档侧 todo 块勾选态变化事件（V10）：upsert 更新路径检测到 todo 的 checked 变化时由 document 模块发布，board
 * 模块监听并同步对应看板卡片，完成文档→看板反向同步。
 *
 * <p>放置于 shared：board 与 document 均依赖 shared，避免两模块互相依赖成环。
 */
public record BlockCheckedEvent(UUID blockId, boolean checked, UUID documentId) {}
