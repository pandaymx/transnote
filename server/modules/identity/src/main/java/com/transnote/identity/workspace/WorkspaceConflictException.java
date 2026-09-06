package com.transnote.identity.workspace;

/** 业务冲突（slug 重复等，映射 HTTP 422）。 */
public class WorkspaceConflictException extends RuntimeException {

  public WorkspaceConflictException(String message) {
    super(message);
  }
}
