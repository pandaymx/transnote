package com.transnote.api.document;

import com.transnote.api.ApiResponse;
import com.transnote.document.model.Document;
import com.transnote.document.service.BlockService;
import com.transnote.document.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文档与块 REST API（契约 §7.3）。 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

  private final DocumentService documentService;
  private final BlockService blockService;

  public DocumentController(DocumentService documentService, BlockService blockService) {
    this.documentService = documentService;
    this.blockService = blockService;
  }

  @PostMapping
  public ApiResponse<DocumentResponse> create(@Valid @RequestBody CreateDocumentRequest request) {
    Document created =
        documentService.create(request.workspaceId(), request.title(), request.icon());
    return ApiResponse.ok(DocumentResponse.from(created));
  }

  @GetMapping
  public ApiResponse<List<DocumentResponse>> list(@RequestParam("workspaceId") UUID workspaceId) {
    return ApiResponse.ok(
        documentService.listByWorkspace(workspaceId).stream().map(DocumentResponse::from).toList());
  }

  @GetMapping("/{id}")
  public ApiResponse<DocumentTreeResponse> getTree(@PathVariable UUID id) {
    Document document = documentService.get(id);
    List<BlockNodeResponse> blocks =
        blockService.tree(id).stream().map(BlockNodeResponse::from).toList();
    return ApiResponse.ok(DocumentTreeResponse.of(document, blocks));
  }

  @PatchMapping("/{id}")
  public ApiResponse<DocumentResponse> rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
    return ApiResponse.ok(DocumentResponse.from(documentService.rename(id, request.title())));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable UUID id) {
    documentService.delete(id);
    return ApiResponse.ok();
  }

  /** 批量块操作：updates 逐个应用（upsert|delete|move），同一事务。 */
  @PatchMapping("/{id}/blocks")
  public ApiResponse<Void> updateBlocks(
      @PathVariable UUID id, @Valid @RequestBody UpdateBlocksRequest request) {
    documentService.get(id); // 文档不存在 → 404
    for (BlockUpdate update : request.updates()) {
      applyUpdate(id, update);
    }
    return ApiResponse.ok();
  }

  private void applyUpdate(UUID documentId, BlockUpdate update) {
    BlockPayload block = update.block();
    if (block == null) {
      throw new IllegalArgumentException("block 不能为空");
    }
    switch (update.op()) {
      case "upsert" ->
          blockService.upsert(
              documentId,
              block.id(),
              block.parentId(),
              block.type(),
              block.content(),
              block.properties(),
              block.position());
      case "delete" -> blockService.delete(documentId, block.id());
      case "move" -> blockService.move(documentId, block.id(), block.parentId(), block.position());
      default ->
          throw new IllegalArgumentException(
              "不支持的 op: " + update.op() + "（可选: upsert/delete/move）");
    }
  }
}
