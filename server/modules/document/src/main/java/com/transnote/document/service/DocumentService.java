package com.transnote.document.service;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardCard;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Document;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
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
    String text = textOf(node);
    if ("todo".equals(node.type()) && StringUtils.hasText(text)) {
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
          text,
          null,
          null,
          null,
          null,
          null,
          null,
          documentId,
          node.id(),
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

  /** 看板 → 文档：列转标题块、卡片转 todo 块；无 documentId 时按看板名自动建文档。 */
  @Transactional
  public ToDocumentResult toDocument(UUID boardId, UUID documentId) {
    Board board = boardService.get(boardId);
    Document document;
    if (documentId != null) {
      document = get(documentId);
    } else {
      document = create(board.getWorkspace().getId(), board.getTitle(), "📋");
    }
    int created = 0;
    for (BoardColumn column : boardService.columns(boardId)) {
      blockService.upsert(document.getId(), null, null, "heading_2", column.getTitle(), null, null);
      for (BoardCard card : boardService.listCards(boardId, column.getId(), null, null)) {
        blockService.upsert(
            document.getId(),
            null,
            null,
            "todo",
            card.getTitle(),
            "{\"checked\":" + card.isChecked() + "}",
            null);
        created++;
      }
    }
    return new ToDocumentResult(document.getId(), created);
  }

  /** 看板 → 文档结果。 */
  public record ToDocumentResult(UUID documentId, int created) {}

  /** 文档 → Word：块树渲染为 docx 字节流。 */
  public byte[] exportWord(UUID documentId) {
    Document document = get(documentId);
    try (XWPFDocument docx = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph title = docx.createParagraph();
      title.setAlignment(ParagraphAlignment.CENTER);
      XWPFRun titleRun = title.createRun();
      titleRun.setBold(true);
      titleRun.setFontSize(20);
      titleRun.setText(StringUtils.hasText(document.getTitle()) ? document.getTitle() : "未命名文档");
      renderChildren(blockService.tree(documentId), docx);
      docx.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("文档导出 Word 失败", e);
    }
  }

  private void renderChildren(List<BlockNode> nodes, XWPFDocument docx) {
    int numbered = 1;
    for (BlockNode node : nodes) {
      renderNode(node, docx, numbered);
      if ("numbered_list".equals(node.type())) {
        numbered++;
      } else {
        numbered = 1;
      }
    }
  }

  private void renderNode(BlockNode node, XWPFDocument docx, int numbered) {
    String text = textOf(node);
    switch (node.type()) {
      case "heading_1" -> addParagraph(docx, text, 18, true, false, null);
      case "heading_2" -> addParagraph(docx, text, 15, true, false, null);
      case "heading_3" -> addParagraph(docx, text, 13, true, false, null);
      case "todo" ->
          addParagraph(
              docx, (isChecked(node) ? "☑ " : "☐ ") + text, 11, false, isChecked(node), null);
      case "bulleted_list" -> addParagraph(docx, "• " + text, 11, false, false, null);
      case "numbered_list" -> addParagraph(docx, numbered + ". " + text, 11, false, false, null);
      case "quote" -> addParagraph(docx, text, 11, false, true, null);
      case "code" -> addParagraph(docx, text, 10, false, false, "Consolas");
      case "divider" -> {
        XWPFParagraph p = docx.createParagraph();
        XWPFRun run = p.createRun();
        run.setText("― ― ― ―");
        run.setColor("9B9A97");
      }
      case "toggle" -> addParagraph(docx, "▸ " + text, 12, true, false, null);
      default -> addParagraph(docx, text, 11, false, false, null);
    }
    for (BlockNode child : node.children()) {
      renderNode(child, docx, 1);
    }
  }

  private void addParagraph(
      XWPFDocument docx, String text, int size, boolean bold, boolean italic, String fontFamily) {
    XWPFParagraph p = docx.createParagraph();
    XWPFRun run = p.createRun();
    run.setFontSize(size);
    run.setBold(bold);
    run.setItalic(italic);
    if (fontFamily != null) {
      run.setFontFamily(fontFamily);
    }
    if (StringUtils.hasText(text)) {
      run.setText(text);
    }
  }

  private boolean isChecked(BlockNode node) {
    if (!StringUtils.hasText(node.properties())) {
      return false;
    }
    try {
      return Boolean.parseBoolean(
          objectMapper.readTree(node.properties()).path("checked").asText("false"));
    } catch (Exception ignored) {
      return false;
    }
  }

  /** content 契约为 JSON 字符串字面量（如 "正文"）；解析失败按原样文本兼容旧数据。 */
  private String textOf(BlockNode node) {
    if (node.content() == null) {
      return "";
    }
    try {
      JsonNode n = objectMapper.readTree(node.content());
      return n.isTextual() ? n.asText() : "";
    } catch (Exception e) {
      return node.content();
    }
  }

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

  /** 更新图标（emoji 或空串清除）。 */
  @Transactional
  public Document updateIcon(UUID id, String icon) {
    Document document = get(id);
    document.setIcon(StringUtils.hasText(icon) ? icon.trim() : null);
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
