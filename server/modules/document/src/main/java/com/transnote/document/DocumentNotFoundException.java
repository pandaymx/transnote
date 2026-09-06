package com.transnote.document;

import java.util.UUID;

/** 文档不存在（映射 HTTP 404）。 */
public class DocumentNotFoundException extends RuntimeException {

  private final UUID id;

  public DocumentNotFoundException(UUID id) {
    super("文档不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
