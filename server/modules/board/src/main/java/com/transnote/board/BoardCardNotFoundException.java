package com.transnote.board;

import java.util.UUID;

/** 看板卡片不存在（映射 HTTP 404）。 */
public class BoardCardNotFoundException extends RuntimeException {

  private final UUID id;

  public BoardCardNotFoundException(UUID id) {
    super("看板卡片不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
