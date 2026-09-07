package com.transnote.document.service;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Document;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/** 文档服务：创建/列表/详情/改名，以及文档 → 看板（todo 块转卡片）。 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

  private static final int MAX_TITLE_LENGTH = 500;
  private static final int MAX_ICON_LENGTH = 64;

  private final DocumentRepository repository;
  private final WorkspaceService workspaceService;
  private final BlockService blockService;
  private final BoardService boardService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DocumentService(
      DocumentRepository repository,
      WorkspaceService workspaceService,
      BlockService blockService,
      BoardService boardService) {
    this.repository = repository;
    this.workspaceService = workspaceService;
    this.blockService = blockService;
    this.boardService = boardService;
  }

  /** 文档 → 看板：全部 todo 块转为卡片；无 boardId 时按文档名自动建看板。 */
  @Transactional
  public ToBoardResult toBoard(UUID documentId, UUID boardId) {
    Document document = get(documentId);
    Board board;
    if (boardId != null) {
      board = boardService.get(boardId);
    } else {
      board =
          boardService.create(document.getWorkspace().getId(), document.getTitle(), "kanban", null);
    }
    List<BoardColumn> columns = boardService.columns(board.getId());
    BoardColumn target =
        columns.isEmpty() ? boardService.addColumn(board.getId(), "待办", "gray") : columns.get(0);

    int created = 0;
    for (BlockNode node : blockService.tree(documentId)) {
      created += collectTodo(node, board.getId(), target.getId(), documentId);
    }
    return new ToBoardResult(board.getId(), created);
  }

  private int collectTodo(BlockNode node, UUID boardId, UUID columnId, UUID documentId) {
    int created = 0;
    if ("todo".equals(node.type()) && StringUtils.hasText(node.content())) {
      boolean checked = false;
      if (StringUtils.hasText(node.properties())) {
        try {
          checked =
              Boolean.parseBoolean(
                  objectMapper.readTree(node.properties()).path("checked").asText("false"));
        } catch (Exception ignored) {
          // 属性解析失败按未勾选处理
        }
      }
      boardService.addCard(
          boardId,
          columnId,
          node.content(),
          null,
          null,
          null,
          null,
          null,
          null,
          documentId,
          null,
          checked);
      created++;
    }
    for (BlockNode child : node.children()) {
      created += collectTodo(child, boardId, columnId, documentId);
    }
    return created;
  }

  /** 文档 → 看板结果。 */
  public record ToBoardResult(UUID boardId, int created) {}

  @Transactional
  public Document create(UUID workspaceId, String title, String icon) {
    if (StringUtils.hasText(title) && title.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + MAX_TITLE_LENGTH + " 字符");
    }
    if (StringUtils.hasText(icon) && icon.length() > MAX_ICON_LENGTH) {
      throw new IllegalArgumentException("icon 不能超过 " + MAX_ICON_LENGTH + " 字符");
    }
    Workspace workspace = workspaceService.get(workspaceId); // 不存在抛 404
    String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "";
    String normalizedIcon = StringUtils.hasText(icon) ? icon.trim() : null;
    return repository.save(new Document(workspace, normalizedTitle, normalizedIcon));
  }

  public Document get(UUID id) {
    return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
  }

  public List<Document> listByWorkspace(UUID workspaceId) {
    return repository.findByWorkspace_IdOrderByUpdatedAtDesc(workspaceId);
  }

  @Transactional
  public Document rename(UUID id, String title) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    if (title.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + MAX_TITLE_LENGTH + " 字符");
    }
    Document document = get(id);
    document.rename(title.trim());
    return repository.save(document);
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new DocumentNotFoundException(id);
    }
    repository.deleteById(id);
  }
}
