package com.transnote.board;

import java.util.UUID;

/** 看板不存在（映射 HTTP 404）。 */
public class BoardNotFoundException extends RuntimeException {

  private final UUID id;

  public BoardNotFoundException(UUID id) {
    super("看板不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
