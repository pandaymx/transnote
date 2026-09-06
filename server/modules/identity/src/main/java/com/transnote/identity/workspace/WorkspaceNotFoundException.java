package com.transnote.identity.workspace;

import java.util.UUID;

/** 工作区不存在（映射 HTTP 404）。 */
public class WorkspaceNotFoundException extends RuntimeException {

  private final UUID id;

  public WorkspaceNotFoundException(UUID id) {
    super("工作区不存在: " + id);
    this.id = id;
  }

  public UUID getId() {
    return id;
  }
}
