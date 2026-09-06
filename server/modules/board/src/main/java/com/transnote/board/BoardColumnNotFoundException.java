package com.transnote.board;

import java.util.UUID;

/** 看板列不存在（映射 HTTP 404）。 */
public class BoardColumnNotFoundException extends RuntimeException {

  private final UUID id;

  public BoardColumnNotFoundException(UUID id) {
    super("看板列不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
