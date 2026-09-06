package com.transnote.api.document;

import com.transnote.document.service.BlockNode;
import java.util.List;
import java.util.UUID;

/** 块树响应节点（children 为嵌套子块）。 */
public record BlockNodeResponse(
    UUID id,
    UUID parentId,
    String type,
    String content,
    String properties,
    int position,
    long version,
    List<BlockNodeResponse> children) {

  public static BlockNodeResponse from(BlockNode node) {
    return new BlockNodeResponse(
        node.id(),
        node.parentId(),
        node.type(),
        node.content(),
        node.properties(),
        node.position(),
        node.version(),
        node.children().stream().map(BlockNodeResponse::from).toList());
  }
}
