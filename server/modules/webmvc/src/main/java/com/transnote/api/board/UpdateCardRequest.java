package com.transnote.api.board;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 卡片部分更新；columnId/position 同时出现（或任一出现）时按"拖拽"处理（换列+重排一次提交）。 */
public record UpdateCardRequest(
    String title,
    String description,
    UUID assigneeId,
    LocalDate dueDate,
    Short priority,
    List<String> labels,
    UUID columnId,
    Integer position) {

  public boolean isDrag() {
    return columnId != null || position != null;
  }
}
