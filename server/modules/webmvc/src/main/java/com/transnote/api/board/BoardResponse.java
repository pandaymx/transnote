package com.transnote.api.board;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardColumn;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BoardResponse(
    UUID id,
    UUID workspaceId,
    String title,
    String layout,
    String config,
    List<BoardColumnResponse> columns,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static BoardResponse from(Board board) {
    return from(board, null);
  }

  public static BoardResponse from(Board board, List<BoardColumn> columns) {
    List<BoardColumnResponse> columnResponses =
        columns == null
            ? null
            : columns.stream()
                .map(
                    c ->
                        new BoardColumnResponse(
                            c.getId(), c.getTitle(), c.getPosition(), c.getStatusColor()))
                .toList();
    return new BoardResponse(
        board.getId(),
        board.getWorkspaceId(),
        board.getTitle(),
        board.getLayout(),
        board.getConfig(),
        columnResponses,
        board.getCreatedAt(),
        board.getUpdatedAt());
  }
}
