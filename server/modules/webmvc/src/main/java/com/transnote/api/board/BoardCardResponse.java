package com.transnote.api.board;

import com.transnote.board.model.BoardCard;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BoardCardResponse(
    UUID id,
    UUID boardId,
    UUID columnId,
    int position,
    String title,
    String description,
    UUID assigneeId,
    String assigneeName,
    LocalDate dueDate,
    short priority,
    List<String> labels,
    boolean checked,
    UUID sourceDocumentId,
    String sourceEvidence,
    long version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static BoardCardResponse from(BoardCard card) {
    return new BoardCardResponse(
        card.getId(),
        card.getBoard().getId(),
        card.getColumn().getId(),
        card.getPosition(),
        card.getTitle(),
        card.getDescription(),
        card.getAssigneeId(),
        card.getAssigneeName(),
        card.getDueDate(),
        card.getPriority(),
        card.getLabels(),
        card.isChecked(),
        card.getSourceDocumentId(),
        card.getSourceEvidence(),
        card.getVersion(),
        card.getCreatedAt(),
        card.getUpdatedAt());
  }
}
