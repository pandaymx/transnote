package com.transnote.board.event;

import java.util.UUID;

/** 卡片勾选态变更事件（V9）：board 模块发布，document 模块监听回写源文档 todo 块。 解耦 board→document 模块环：board 不依赖 document。 */
public record CardCheckedEvent(
    UUID boardId, UUID cardId, boolean checked, UUID sourceDocumentId, UUID sourceBlockId) {}
