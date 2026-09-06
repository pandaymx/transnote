package com.transnote.api.document;

import com.transnote.document.model.Document;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID workspaceId,
    String title,
    String icon,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static DocumentResponse from(Document document) {
    return new DocumentResponse(
        document.getId(),
        document.getWorkspaceId(),
        document.getTitle(),
        document.getIcon(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
