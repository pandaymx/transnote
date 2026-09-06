package com.transnote.api.document;

import java.util.UUID;

/** 块操作负载（upsert/delete/move 共用，按 op 取用字段）。 */
public record BlockPayload(
    UUID id, UUID parentId, String type, String content, String properties, Integer position) {}
