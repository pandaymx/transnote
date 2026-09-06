package com.transnote.api.workspace;

import com.transnote.identity.workspace.Workspace;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id, String name, String slug, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

  public static WorkspaceResponse from(Workspace workspace) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getSlug(),
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }
}
