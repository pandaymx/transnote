package com.transnote.api.board;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AddCardRequest(
    UUID columnId,
    String title,
    String description,
    UUID assigneeId,
    LocalDate dueDate,
    Short priority,
    List<String> labels,
    UUID sourceDocumentId,
    String sourceEvidence) {}
