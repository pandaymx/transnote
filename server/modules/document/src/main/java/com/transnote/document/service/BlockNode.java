package com.transnote.document.service;

import java.util.List;
import java.util.UUID;

/** 块树节点（整文档树组装结果）。 */
public record BlockNode(
    UUID id,
    UUID parentId,
    String type,
    String content,
    String properties,
    int position,
    long version,
    List<BlockNode> children) {}
