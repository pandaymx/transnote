package com.transnote.api.document;

import com.transnote.document.model.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 文档 + 整块树（GET /api/v1/documents/{id}）。 */
public record DocumentTreeResponse(
    UUID id,
    UUID workspaceId,
    String title,
    String icon,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<BlockNodeResponse> blocks) {

  public static DocumentTreeResponse of(Document document, List<BlockNodeResponse> blocks) {
    return new DocumentTreeResponse(
        document.getId(),
        document.getWorkspaceId(),
        document.getTitle(),
        document.getIcon(),
        document.getCreatedAt(),
        document.getUpdatedAt(),
        blocks);
  }
}
