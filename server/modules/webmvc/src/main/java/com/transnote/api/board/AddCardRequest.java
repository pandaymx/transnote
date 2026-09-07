package com.transnote.api.board;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 建卡请求；assigneeName 为无用户体系的过渡负责人名，checked 为卡片级完成态（V6，Notion 勾选）。 */
public record AddCardRequest(
    UUID columnId,
    String title,
    String description,
    UUID assigneeId,
    String assigneeName,
    LocalDate dueDate,
    Short priority,
    List<String> labels,
    UUID sourceDocumentId,
    String sourceEvidence,
    Boolean checked) {}
