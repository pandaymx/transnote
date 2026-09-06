package com.transnote.api.document;

/** 单条块操作：op = upsert | delete | move。 */
public record BlockUpdate(String op, BlockPayload block) {}
