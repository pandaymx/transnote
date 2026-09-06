package com.transnote.document;

import java.util.UUID;

/** 块不存在（映射 HTTP 404）。 */
public class BlockNotFoundException extends RuntimeException {

  private final UUID id;

  public BlockNotFoundException(UUID id) {
    super("块不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
